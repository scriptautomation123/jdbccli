
git clone git@scriptautomation123:scriptautomation123/jdbccli.git


# setup
need to use 

git clone git@scriptautomation123:scriptautomation123/jdbccli.git

./setup-ssh-git.sh

imaging you have 2 github accounts scriptautomation123 and swapan-datahawklab

cd ~/scriptautomation123 &&\
git clone git@scriptautomation123:scriptautomation123/jets4best.git

cd ~/swapan-datahawklab &&\
git clone git@swapan-datahawklab:swapan-datahawklab/somerepo.git



scriptautomation123/setup





### Resolving GitHub Email Privacy Issues

When pushing to GitHub, you might encounter the following error:
`remote: error: GH007: Your push would publish a private email address.`

This happens because your GitHub account is configured to keep your email address private, but your local Git configuration is using a personal email address (e.g.,  that matches your account's primary email.

#### How to Fix

To resolve this and prevent future push rejections, you should update your Git configuration to use GitHub's "no-reply" email address.

##### 1. Find Your No-Reply Email
GitHub automatically generates a no-reply email for every user in the format:
`ID+USERNAME@users.noreply.github.com`

For this account, the email is:
******

##### 2. Update Your Git Configuration

**To update only this specific repository (Local):**
Run this command from within the project directory:
```powershell
git config user.email "****************@users.noreply.github.com"
```

**To update for all future projects (Global):**
Run:
```powershell
git config --global user.email "**********@users.noreply.github.com"
```

##### 3. Fix Existing Commits
If you have already made commits with the wrong email and they are failing to push, you need to update those commits before pushing again:

```powershell
git commit --amend --reset-author --no-edit
git push origin main
```

#### Why use the No-Reply address?
*   **Privacy:** Keeps your personal email address hidden from the public commit history.
*   **Reliability:** Prevents GitHub from rejecting your pushes due to privacy protections.
*   **Account Linking:** GitHub still recognizes these commits as yours and associates them with your profile (e.g., showing your avatar and counting towards your contribution graph).
echo "# jdbccli" >> README.md
git init
git add README.md
git commit -m "first commit"
git branch -M main
z
git push -u origin main