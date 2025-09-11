
# Dropbox Business API Java Demo

A minimal Java 11 console app that performs OAuth 2.0 Authorization Code flow with Dropbox and calls **/2/team/get_info** to print team name, team ID, and license counts.

## Prerequisites
- Java 11+
- Maven 3.8+
- A Dropbox **Business/Team** account (trial works)
- A Dropbox **Scoped** app created in the [App Console](https://www.dropbox.com/developers/apps) with:
  - Access type: **Full Dropbox** (needed to enable *Team scopes*)
  - Redirect URI: `http://localhost:8080/callback`
  - Team scopes enabled: `team_info.read` (plus `members.read` and `events.read` if you plan to call those later)

## Run
```bash
export DROPBOX_CLIENT_ID=YOUR_APP_KEY
export DROPBOX_CLIENT_SECRET=YOUR_APP_SECRET
export DROPBOX_REDIRECT_URI=http://localhost:8080/callback

mvn -q -e exec:java
```
The app prints an authorization URL. Open it, sign in as a **team admin**, approve, and you'll see the team info printed in the console.

## What it does
- Spins up a tiny HTTP server on `localhost:8080` to receive the OAuth redirect
- Exchanges the authorization code for tokens (requests an **offline** token so a refresh token is issued)
- Calls `POST https://api.dropboxapi.com/2/team/get_info` with the access token and prints the JSON
- If a 401 occurs, refreshes the access token using the refresh token
```
