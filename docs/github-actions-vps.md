# GitHub Actions Deployment to VPS

This guide configures automatic deployment from GitHub Actions to your VPS using SSH.

## 1. What was added

- CI workflow: `.github/workflows/ci.yml`
  - Runs backend tests (`mvn -q test`)
  - Runs frontend build (`npm run build`)
- Deploy workflow: `.github/workflows/deploy-vps.yml`
  - Trigger after `CI` succeeds for a push to `master`
  - Manual trigger from Actions tab (`workflow_dispatch`)
- Remote deploy script: `scripts/deploy/vps_deploy.sh`
  - Pulls latest branch on VPS
  - Runs `docker compose up -d --build` with your existing `infra/.env`

## 2. One-time VPS bootstrap

Run this once on the VPS:

```bash
sudo mkdir -p /opt
cd /opt
sudo git clone https://github.com/<your-user>/<your-repo>.git PilarEstilo
sudo chown -R $USER:$USER /opt/PilarEstilo

cd /opt/PilarEstilo
cp infra/.env.example infra/.env
nano infra/.env
```

Then make sure Docker is installed and working for your user:

```bash
docker --version
docker compose version
```

## 3. Create deploy key for GitHub Actions

On your local machine:

```bash
ssh-keygen -t ed25519 -C "gha-deploy@pilarestilo" -f ./gha_vps_deploy_key -N ""
```

This creates:

- `gha_vps_deploy_key` (private key)
- `gha_vps_deploy_key.pub` (public key)

Add public key to VPS user:

```bash
ssh <user>@<vps-host>
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo "<paste gha_vps_deploy_key.pub>" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

## 4. Configure GitHub Secrets

In GitHub repo: `Settings -> Secrets and variables -> Actions -> New repository secret`

Create:

- `VPS_HOST`: VPS public IP or domain
- `VPS_PORT`: SSH port (usually `22`)
- `VPS_USER`: deploy user on VPS
- `VPS_SSH_KEY`: private key content from `gha_vps_deploy_key`
- `VPS_APP_DIR`: app directory on VPS (example: `/opt/PilarEstilo`)
- `VPS_COMPOSE_PROFILES` (optional): example `cache,microservices`

## 5. How deploy works

After a successful `CI` run for `master`, workflow `Deploy VPS`:

1. Connects over SSH
2. Runs `scripts/deploy/vps_deploy.sh` in VPS
3. Script executes:
   - `git fetch --prune`
   - `git checkout <branch>`
   - `git pull --ff-only`
   - `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build`

## 6. Manual deploy options

From `Actions -> Deploy VPS -> Run workflow` you can set:

- `branch`: branch to deploy (default `master`)
- `compose_profiles`: optional compose profiles
- `skip_build`: `true` to skip `--build`

## 7. First run checklist

- VPS has `/opt/PilarEstilo/infra/.env`
- SSH user can run Docker without sudo
- All required GitHub secrets are configured
- Branch `master` exists and is up to date

## 8. Rollback

If needed, deploy a previous commit from VPS:

```bash
cd /opt/PilarEstilo
git checkout <commit-sha>
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```
