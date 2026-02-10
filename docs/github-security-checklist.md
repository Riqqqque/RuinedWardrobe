# GitHub Security Checklist (Private Repo)

## Repository Visibility
1. Settings -> General -> Danger Zone -> Change visibility -> Private.
2. Confirm only trusted collaborators have access.

## Access Controls
1. Enable 2FA on owner and all collaborators.
2. Use fine-grained PATs with minimal scopes.
3. Prefer SSH keys over HTTPS tokens when possible.

## Branch Protection
1. Protect `main`.
2. Require pull requests and at least one review.
3. Require status checks (`CI`) to pass.
4. Block force-pushes and branch deletions.

## Secret Hygiene
1. Never commit DB passwords/tokens.
2. Use GitHub Actions secrets for CI/CD credentials.
3. Keep `.gitignore` covering local server files and runtime DB files.

## Security Automation
1. Enable Dependabot updates (included in `.github/dependabot.yml`).
2. Enable Dependabot alerts + secret scanning in repository Security settings.
3. Optionally enable CodeQL for static analysis.
