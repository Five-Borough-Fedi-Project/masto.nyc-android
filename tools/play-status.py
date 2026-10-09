#!/usr/bin/env python3
"""Prints what Google Play actually has on each track: version codes and release status.

Read-only. Creates an edit, reads the tracks, deletes the edit without committing, so nothing
changes. Needs the service account JSON in GOOGLE_SERVICE_ACCOUNT_KEY.

  python3 tools/play-status.py [package_name]
  python3 tools/play-status.py --expect internal:1004:completed --expect production:1004:completed
  python3 tools/play-status.py --expect-notes production:1004:"The first big update"

--expect takes track:versionCode:status and exits non-zero unless Play agrees, so a release can
check what actually landed rather than trusting a tool's exit code.

--expect-notes takes track:versionCode:substring and checks the release notes as well. Promoting
a release does not carry its notes, and fastlane reports success either way, so a production
release once went out showing a commit subject while every other assertion passed.
"""
import json
import os
import sys
import urllib.request

from google.oauth2 import service_account
import google.auth.transport.requests

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def call(token, method, url, ok_empty=False):
    req = urllib.request.Request(url, method=method, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req) as r:
        body = r.read()
    return json.loads(body) if body else ({} if ok_empty else None)


def check(tracks, expectations):
    """Returns a list of unmet expectations, each as a readable string."""
    seen = {}
    for track in tracks:
        for rel in track.get("releases", []):
            for code in rel.get("versionCodes", []):
                seen[(track["track"], str(code))] = rel.get("status")
    unmet = []
    for want in expectations:
        track, code, status = want.split(":")
        actual = seen.get((track, code))
        if actual != status:
            unmet.append(f"{track}: versionCode {code} is {actual or 'absent'}, expected {status}")
    return unmet


def check_notes(tracks, expectations):
    """Same, for the release notes. The substring may contain colons; only split twice."""
    seen = {}
    for track in tracks:
        for rel in track.get("releases", []):
            text = " ".join(n.get("text", "") for n in rel.get("releaseNotes", []))
            for code in rel.get("versionCodes", []):
                seen[(track["track"], str(code))] = text
    unmet = []
    for want in expectations:
        track, code, substring = want.split(":", 2)
        actual = seen.get((track, code))
        if actual is None:
            unmet.append(f"{track}: versionCode {code} is absent, so it has no notes to check")
        elif substring not in actual:
            first = actual.strip().splitlines()[:1]
            unmet.append(f"{track}: versionCode {code} notes do not contain {substring!r}; "
                         f"they start {first[0] if first else '(empty)'!r}")
    return unmet


def main():
    args = [a for a in sys.argv[1:]]
    expectations = []
    while "--expect" in args:
        i = args.index("--expect")
        expectations.append(args[i + 1])
        del args[i:i + 2]
    note_expectations = []
    while "--expect-notes" in args:
        i = args.index("--expect-notes")
        note_expectations.append(args[i + 1])
        del args[i:i + 2]
    package = args[0] if args else "nyc.masto.android"
    key = json.loads(os.environ["GOOGLE_SERVICE_ACCOUNT_KEY"])
    creds = service_account.Credentials.from_service_account_info(
        key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    creds.refresh(google.auth.transport.requests.Request())
    token = creds.token

    edit = call(token, "POST", f"{API}/{package}/edits")
    edit_id = edit["id"]
    try:
        response = call(token, "GET", f"{API}/{package}/edits/{edit_id}/tracks")
        # The list response holds "tracks"; each entry's own "track" field is its name. Getting
        # this wrong printed nothing and still exited 0, so say so loudly instead.
        tracks = response.get("tracks")
        if not tracks:
            print(f"No tracks came back for {package}. Raw response:")
            print(json.dumps(response, indent=2))
            return 1
        for track in tracks:
            print(f"\ntrack: {track['track']}")
            releases = track.get("releases", [])
            if not releases:
                print("  (no releases)")
            for rel in releases:
                codes = ", ".join(str(c) for c in rel.get("versionCodes", []))
                line = f"  versionCode {codes or '-'}  status={rel.get('status')}"
                if rel.get("name"):
                    line += f"  name={rel['name']}"
                if rel.get("userFraction") is not None:
                    line += f"  userFraction={rel['userFraction']}"
                print(line)
                for note in rel.get("releaseNotes", []):
                    first = note.get("text", "").strip().splitlines()[:1]
                    print(f"    notes[{note.get('language')}]: {first[0] if first else ''}")
        unmet = check(tracks, expectations) + check_notes(tracks, note_expectations)
        if unmet:
            print("\nPlay does not match what was expected:")
            for line in unmet:
                print(f"  {line}")
            return 1
        total = len(expectations) + len(note_expectations)
        if total:
            print(f"\nAll {total} expectation(s) met.")
    finally:
        call(token, "DELETE", f"{API}/{package}/edits/{edit_id}", ok_empty=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
