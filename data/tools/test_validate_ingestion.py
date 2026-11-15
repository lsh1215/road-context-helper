import copy
from datetime import date
import hashlib
import tempfile
from pathlib import Path
import unittest
from validate_ingestion import validate, REQUIRED_CHECKS

TODAY = date(2026, 11, 14)


class IngestionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        files = []
        for suffix in ('.shp', '.dbf', '.shx', '.prj'):
            content = ('TEST ONLY ' + suffix).encode()
            (self.base / ('test' + suffix)).write_bytes(content)
            files.append({'path': 'test' + suffix, 'sha256': hashlib.sha256(content).hexdigest()})
        # Test-only bytes: metadata validator does not approve actual geometry.
        self.good = dict(schemaVersion=1, sourceMode='REAL_WORLD', datasetId='test', sourceUrl='https://example.org/test', license='TEST', attribution='TEST', sourceCrs='TEST', outputCrs='EPSG:4326', acquiredAt='2026-11-13', sourceVersion='test', files=files, review=dict(status='APPROVED', reviewer='test', reviewedAt='2026-11-14', expiresAt='2026-12-14', checks={k: True for k in REQUIRED_CHECKS}))

    def tearDown(self):
        self.temp.cleanup()

    def test_complete_metadata(self):
        self.assertEqual([], validate(self.good, self.base, TODAY))

    def test_hash_changed(self):
        (self.base/'test.shp').write_bytes(b'changed')
        self.assertTrue(any('sha256 mismatch' in e for e in validate(self.good, self.base, TODAY)))

    def test_companion_missing(self):
        self.good['files'] = self.good['files'][:-1]
        self.assertTrue(any('companion' in e for e in validate(self.good, self.base, TODAY)))

    def test_expired_review(self):
        self.good['review']['expiresAt'] = '2026-11-14'
        self.assertIn('review expired', validate(self.good, self.base, TODAY))

    def test_source_not_explicitly_real(self):
        self.good['sourceMode'] = 'REPLAY_SYNTHETIC'
        self.assertTrue(validate(self.good, self.base, TODAY))

    def test_missing_topology_review(self):
        self.good['review']['checks']['mergeEndpoint'] = False
        self.assertIn('review check missing: mergeEndpoint', validate(self.good, self.base, TODAY))

    def test_path_escape_and_env_never_opened(self):
        for path in ('../outside.shp', '/tmp/absolute.shp', '.env', '.env.local', 'secret.env'):
            candidate = copy.deepcopy(self.good)
            candidate['files'][0]['path'] = path
            self.assertTrue(any('unsafe file' in e for e in validate(candidate, self.base, TODAY)))

    def test_future_acquisition(self):
        self.good['acquiredAt'] = '2026-11-15'
        self.assertTrue(validate(self.good, self.base, TODAY))

    def test_bad_shape(self):
        for value in (None, [], 'invalid'):
            self.assertTrue(validate(value, self.base, TODAY))


if __name__ == '__main__':
    unittest.main()
