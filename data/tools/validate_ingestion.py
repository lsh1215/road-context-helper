#!/usr/bin/env python3
"""Check source evidence; never infer actual road topology from metadata alone."""
import argparse
from datetime import date
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlparse

REQUIRED_CHECKS = {'direction', 'lanePersistence', 'mergeEndpoint', 'connectivity', 'crsTransform', 'currentness', 'license'}


def validate(manifest, base: Path, today: date):
    errors = []
    def need(ok, message):
        if not ok:
            errors.append(message)
    def nonempty(value):
        return isinstance(value, str) and bool(value.strip())
    def parsed_date(value, name):
        try:
            return date.fromisoformat(value)
        except (ValueError, TypeError):
            errors.append(name + ': invalid ISO date')
            return None
    need(isinstance(manifest, dict), 'manifest must be an object')
    if not isinstance(manifest, dict):
        return errors
    need(manifest.get('schemaVersion') == 1, 'unsupported schemaVersion')
    need(manifest.get('sourceMode') == 'REAL_WORLD', 'sourceMode must explicitly be REAL_WORLD')
    for key in ('datasetId', 'license', 'attribution', 'sourceCrs', 'sourceVersion'):
        need(nonempty(manifest.get(key)), key + ' is required')
    source = manifest.get('sourceUrl')
    parsed = urlparse(source) if isinstance(source, str) else None
    need(parsed is not None and parsed.scheme == 'https' and bool(parsed.netloc), 'sourceUrl must be an HTTPS URL')
    need(manifest.get('outputCrs') == 'EPSG:4326', 'outputCrs must be EPSG:4326')
    acquired = parsed_date(manifest.get('acquiredAt'), 'acquiredAt')
    if acquired:
        need(acquired <= today, 'acquiredAt cannot be in the future')
    files = manifest.get('files')
    need(isinstance(files, list) and len(files) > 0, 'files must be nonempty')
    listed = set()
    if isinstance(files, list):
        for item in files:
            if not isinstance(item, dict):
                errors.append('file record must be an object')
                continue
            rel = item.get('path')
            checksum = item.get('sha256')
            if not nonempty(rel):
                errors.append('file path required')
                continue
            path = Path(rel)
            forbidden = path.name == '.env' or path.name.endswith('.env') or path.name.startswith('.env.')
            if forbidden or path.is_absolute() or '..' in path.parts:
                errors.append('unsafe file path: ' + rel)
                continue
            full = (base / path).resolve()
            if not full.is_relative_to(base.resolve()):
                errors.append('file path escapes manifest directory: ' + rel)
                continue
            need(rel not in listed, 'duplicate file path: ' + rel)
            listed.add(rel)
            if not full.is_file():
                errors.append('missing file: ' + rel)
                continue
            if not isinstance(checksum, str) or not re.fullmatch('[0-9a-f]{64}', checksum):
                errors.append('invalid sha256: ' + rel)
                continue
            digest = hashlib.sha256()
            with full.open('rb') as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            need(digest.hexdigest() == checksum, 'sha256 mismatch: ' + rel)
        for rel in sorted(listed):
            p = Path(rel)
            if p.suffix.lower() == '.shp':
                for suffix in ('.shx', '.dbf', '.prj'):
                    wanted = str(p.with_suffix(suffix)).lower()
                    need(wanted in {x.lower() for x in listed}, 'missing shapefile companion: ' + wanted)
    review = manifest.get('review')
    if not isinstance(review, dict):
        errors.append('review is required')
    else:
        need(review.get('status') == 'APPROVED', 'review.status must be APPROVED')
        need(nonempty(review.get('reviewer')), 'review.reviewer is required')
        reviewed = parsed_date(review.get('reviewedAt'), 'review.reviewedAt')
        expires = parsed_date(review.get('expiresAt'), 'review.expiresAt')
        if reviewed:
            need(reviewed <= today, 'review date is in the future')
            if acquired:
                need(reviewed >= acquired, 'review precedes acquisition')
        if expires:
            need(expires > today, 'review expired')
            if reviewed:
                need(expires > reviewed, 'expiry precedes review')
        checks = review.get('checks', {})
        for key in sorted(REQUIRED_CHECKS):
            need(isinstance(checks, dict) and checks.get(key) is True, 'review check missing: ' + key)
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--today', type=date.fromisoformat, required=True)
    args = parser.parse_args()
    if args.manifest.name == '.env' or args.manifest.name.startswith('.env.') or args.manifest.name.endswith('.env'):
        parser.error('environment files cannot be read')
    try:
        content = json.loads(args.manifest.read_text())
        errors = validate(content, args.manifest.parent, args.today)
    except (OSError, ValueError) as exc:
        errors = [str(exc)]
    print(json.dumps({'valid': not errors, 'errors': errors, 'note': 'Metadata checks are not independent topology verification.'}, ensure_ascii=False, indent=2))
    raise SystemExit(1 if errors else 0)


if __name__ == '__main__':
    main()
