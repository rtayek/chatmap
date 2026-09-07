# Path check: C:/Program Files/Git/resume

The literal path `C:/Program Files/Git/resume` does not exist.

Command checks:

- PowerShell `Get-Command resume`: no result.
- Git Bash `type resume`: `resume: not found`.
- Git Bash `command -v resume`: no result.

Likely cause: Git Bash MSYS path conversion changed the slash input `/resume`
into `C:/Program Files/Git/resume`. The input is not a Git Bash executable.

The PowerShell execution-policy warning came from the user profile and did not
change the path-check result.
