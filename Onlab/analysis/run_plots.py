import hashlib
import os
import subprocess
import sys
import venv
from pathlib import Path

HERE = Path(__file__).resolve().parent
VENV = HERE / ".venv"
REQUIREMENTS = HERE / "requirements.txt"
INSTALLED_MARKER = VENV / ".requirements.sha256"


def venv_python() -> Path:
    if os.name == "nt":
        return VENV / "Scripts" / "python.exe"
    return VENV / "bin" / "python"


def ensure_environment() -> None:
    # A virtualis kornyezetet csak akkor (ujra)epitjuk, ha meg nincs, vagy valtozott a requirements.txt.
    digest = hashlib.sha256(REQUIREMENTS.read_bytes()).hexdigest()
    if venv_python().exists() and INSTALLED_MARKER.exists() and INSTALLED_MARKER.read_text().strip() == digest:
        return
    if not venv_python().exists():
        print(f"[analysis] Virtualis Python kornyezet letrehozasa: {VENV}", flush=True)
        venv.EnvBuilder(with_pip=True).create(VENV)
    print("[analysis] Csomagok telepitese (requirements.txt) ...", flush=True)
    subprocess.run(
        [str(venv_python()), "-m", "pip", "install", "--disable-pip-version-check", "--quiet",
         "-r", str(REQUIREMENTS)],
        check=True,
    )
    INSTALLED_MARKER.write_text(digest)


def main() -> int:
    try:
        ensure_environment()
    except subprocess.CalledProcessError as error:
        print("[analysis] A csomagok telepitese nem sikerult. Ha proxy mogott vagy, allitsd be a "
              "HTTPS_PROXY kornyezeti valtozot, majd futtasd ujra.", file=sys.stderr)
        return error.returncode or 1
    return subprocess.run([str(venv_python()), str(HERE / "plots.py"), *sys.argv[1:]]).returncode


if __name__ == "__main__":
    sys.exit(main())
