#!/usr/bin/env python3
"""Prints what Google Play actually has on each track: version codes and release status.

Read-only. Creates an edit, reads the tracks, deletes the edit without committing, so nothing
changes. Needs the service account JSON in GOOGLE_SERVICE_ACCOUNT_KEY.

  python3 tools/play-status.py [package_name]
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


def main():
    package = sys.argv[1] if len(sys.argv) > 1 else "nyc.masto.android"
    key = json.loads(os.environ["GOOGLE_SERVICE_ACCOUNT_KEY"])
    creds = service_account.Credentials.from_service_account_info(
        key, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    creds.refresh(google.auth.transport.requests.Request())
    token = creds.token

    edit = call(token, "POST", f"{API}/{package}/edits")
    edit_id = edit["id"]
    try:
        tracks = call(token, "GET", f"{API}/{package}/edits/{edit_id}/tracks")
        for track in tracks.get("track", []):
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
    finally:
        call(token, "DELETE", f"{API}/{package}/edits/{edit_id}", ok_empty=True)


if __name__ == "__main__":
    main()
