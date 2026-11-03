"""Rebuild the new collection; preserve the existing companion catalogue."""
import runpy
from pathlib import Path

if __name__ == '__main__':
    runpy.run_path(str(Path(__file__).with_name('build_v2.py')), run_name='__main__')
