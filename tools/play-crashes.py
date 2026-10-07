#!/usr/bin/env python3
"""Prints the crashes and ANRs Google Play has collected, with their stack traces.

Read-only, through the Play Developer Reporting API. Needs the service account JSON in
GOOGLE_SERVICE_ACCOUNT_KEY, with "View app information and download bulk reports" granted to it
in Play Console; the androidpublisher permission the release pipeline uses is not enough, and a
403 here says exactly that.

  python3 tools/play-crashes.py                      # crashes from the last 7 days
  python3 tools/play-crashes.py --days 30 --limit 5
  python3 tools/play-crashes.py --anrs               # ANRs instead of crashes
  python3 tools/play-crashes.py --dry-run            # print the request and stop, no credentials
  python3 tools/play-crashes.py --check              # check the request against Google's schema

This exists because a crash was reported from a real phone, could not be reproduced by hand, and
the only evidence available was the reporter's description. Play had the stack trace the whole
time; nothing here could read it.

--check is worth knowing about. The service account key only exists in CI, so this file cannot be
run end to end on a laptop, which is a good way to ship a script that has never worked. The API
publishes a discovery document, unauthenticated, that declares every query parameter and every
response field. --check reads it and fails if this script sends a parameter the API doesn't take
or reads a field it doesn't return. Writing it caught three real mistakes: errorReportCounts for
errorReportCount, an OsVersion.versionString that doesn't exist, and, worst, a missing
sampleErrorReportLimit, which defaults to 0 and would have made every stack trace come back empty.

Play aggregates with a few hours' delay and withholds data for very small audiences, so an empty
result is not proof there were no crashes.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

API = "https://playdeveloperreporting.googleapis.com/v1beta1"
DISCOVERY = "https://playdeveloperreporting.googleapis.com/$discovery/rest?version=v1beta1"
SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"
SEARCH_METHOD = "playdeveloperreporting.vitals.errors.issues.search"

# Every response field this script reads, as schema name -> fields. --check holds these against
# the API's own declaration, so an upstream rename fails here rather than printing a blank column.
READS = {
    "SearchErrorIssuesResponse": ["errorIssues"],
    "ErrorIssue": ["type", "cause", "location", "errorReportCount", "distinctUsers",
                   "firstAppVersion", "lastAppVersion", "sampleErrorReports", "issueUri",
                   "lastErrorReportTime"],
    "ErrorReport": ["reportText", "deviceModel", "osVersion"],
    "AppVersion": ["versionCode"],
    "OsVersion": ["apiLevel"],
    "DeviceModelSummary": ["marketingName"],
}


def call(token, url):
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        if e.code in (401, 403):
            print(f"Play refused the request ({e.code}). The service account can publish, but "
                  f"probably can't read vitals.\n"
                  f"In Play Console: Users and permissions > the service account > App permissions, "
                  f"and grant \"View app information and download bulk reports\".\n\n{body}",
                  file=sys.stderr)
            sys.exit(2)
        print(f"HTTP {e.code} from {url}\n{body}", file=sys.stderr)
        sys.exit(2)


def interval(days):
    """The API takes the bounds as separate fields rather than timestamps. Whole hours, UTC."""
    end = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    start = end - timedelta(days=days)
    out = {}
    for name, when in (("startTime", start), ("endTime", end)):
        out.update({
            f"interval.{name}.year": when.year,
            f"interval.{name}.month": when.month,
            f"interval.{name}.day": when.day,
            f"interval.{name}.hours": when.hour,
            f"interval.{name}.timeZone.id": "UTC",
        })
    return out


def request_params(kind, days, limit):
    params = dict(interval(days))
    # errorIssueType takes ANR here, even though the type on the response reads
    # APPLICATION_NOT_RESPONDING.
    params["filter"] = f"errorIssueType = {kind}"
    params["pageSize"] = limit
    params["orderBy"] = "errorReportCount desc"
    # Defaults to 0, and then nothing carries a stack trace. Only 0 and 1 are accepted.
    params["sampleErrorReportLimit"] = 1
    return params


def check_against_discovery(params):
    """Holds the request, and the fields READS names, against the API's published schema."""
    with urllib.request.urlopen(DISCOVERY) as r:
        doc = json.loads(r.read())

    def find_method(resources):
        for resource in resources.values():
            for method in (resource.get("methods") or {}).values():
                if method["id"] == SEARCH_METHOD:
                    return method
            found = find_method(resource.get("resources") or {})
            if found:
                return found
        return None

    method = find_method(doc["resources"])
    if not method:
        print(f"the API no longer declares {SEARCH_METHOD}", file=sys.stderr)
        return 1

    problems = []
    declared = set(method.get("parameters", {}))
    for name in params:
        if name not in declared:
            problems.append(f"sends {name!r}, which {SEARCH_METHOD} does not accept")
    print(f"request:  {len(params)} parameters checked")

    schemas = {name.split("1beta1")[-1]: body for name, body in doc["schemas"].items()}
    for schema, fields in READS.items():
        if schema not in schemas:
            problems.append(f"reads schema {schema}, which no longer exists")
            continue
        properties = schemas[schema].get("properties", {})
        for field in fields:
            if field not in properties:
                problems.append(f"reads {schema}.{field}, which the API does not return")
    print(f"response: {sum(len(f) for f in READS.values())} fields across {len(READS)} schemas "
          f"checked")

    if problems:
        print("\nThis script and the API disagree:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print("\nEverything this script sends and reads is in the API's published schema.")
    return 0


def print_issue(issue, token):
    kind = {"CRASH": "crash", "APPLICATION_NOT_RESPONDING": "ANR",
            "NON_FATAL": "handled error"}.get(issue.get("type"), issue.get("type", "error"))
    print(f"{kind}: {issue.get('errorReportCount', '?')} reports from "
          f"{issue.get('distinctUsers', '?')} users")
    if issue.get("cause"):
        print(f"  cause:    {issue['cause']}")
    if issue.get("location"):
        print(f"  location: {issue['location']}")
    first = issue.get("firstAppVersion", {}).get("versionCode")
    last = issue.get("lastAppVersion", {}).get("versionCode")
    if first or last:
        print(f"  versions: {first or '?'} to {last or '?'}")
    if issue.get("lastErrorReportTime"):
        print(f"  last:     {issue['lastErrorReportTime']}")

    samples = issue.get("sampleErrorReports", [])
    if samples:
        report = call(token, f"{API}/{samples[0]}")
        device = report.get("deviceModel", {}).get("marketingName")
        api_level = report.get("osVersion", {}).get("apiLevel")
        if device or api_level:
            print(f"  seen on:  {device or '?'}, API {api_level or '?'}")
        text = (report.get("reportText") or "").strip()
        if text:
            print("  stack trace:")
            for line in text.splitlines()[:30]:
                print(f"    {line}")
    if issue.get("issueUri"):
        print(f"  in Play Console: {issue['issueUri']}")


def main():
    args = sys.argv[1:]

    def flag(name):
        if name in args:
            args.remove(name)
            return True
        return False

    def option(name, default):
        if name in args:
            i = args.index(name)
            value = args[i + 1]
            del args[i:i + 2]
            return value
        return default

    check = flag("--check")
    dry_run = flag("--dry-run")
    anrs = flag("--anrs")
    fail_on_new = flag("--fail-on-new")
    days = int(option("--days", "7"))
    limit = int(option("--limit", "10"))
    package = args[0] if args else "nyc.masto.android"

    params = request_params("ANR" if anrs else "CRASH", days, limit)

    if check:
        return check_against_discovery(params)
    if dry_run:
        print(f"GET {API}/apps/{package}/errorIssues:search?"
              + urllib.parse.urlencode(params).replace("&", "\n    &"))
        print(f"\nscope: {SCOPE}")
        return 0

    # Imported here, not at the top, so --check and --dry-run work without google-auth installed.
    from google.oauth2 import service_account
    import google.auth.transport.requests

    key = json.loads(os.environ["GOOGLE_SERVICE_ACCOUNT_KEY"])
    creds = service_account.Credentials.from_service_account_info(key, scopes=[SCOPE])
    creds.refresh(google.auth.transport.requests.Request())
    token = creds.token

    print(f"{'ANRs' if anrs else 'Crashes'} for {package}, last {days} days")
    url = f"{API}/apps/{package}/errorIssues:search?" + urllib.parse.urlencode(params)
    issues = call(token, url).get("errorIssues", [])
    if not issues:
        print("\nNothing reported. Play aggregates with a few hours' delay and withholds data for\n"
              "very small audiences, so this is not proof there were none.")
        return 0

    for n, issue in enumerate(issues, 1):
        print(f"\n--- {n} " + "-" * 60)
        print_issue(issue, token)

    if fail_on_new:
        print(f"\n{len(issues)} issue(s) in the last {days} days.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
