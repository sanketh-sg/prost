# Prost - Group 6 project

# Technical
Java version: **17**
## Database
Login credentials (set up in resources/application.yml):
```yaml
username: prostadmin
password: strongpassword
url: jdbc:h2:mem:prost
```

By default, the database recreates DDL on every restart. (on "dev" profile).

## Frontend
Dependencies:
+ Node.js
+ package manager (preferably [pnpm](https://pnpm.io/))

Build process:
```bash
pnpm install
pnpm build
```

Frontend built with:
+ [PostCSS](https://postcss.org/)
+ [TailwindCSS](https://tailwindcss.com/)
+ [Font Awesome](https://fontawesome.com/)
