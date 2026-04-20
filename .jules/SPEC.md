# App 06: Employee Onboarding Orchestrator

## Overview
A new module under `apps/06-employee-onboarding-orchestrator` that manages structured onboarding plans for new hires. It allows HR managers to create onboarding templates containing tasks with different types and assignees, and then start an "onboarding instance" for a new hire. The app sends a magic link to the new hire to access their portal and complete tasks.

## Architecture Rules
1. **Module Location**: Create the app under `apps/06-employee-onboarding-orchestrator`.
2. **Dependency**: `pom.xml` MUST have `saas-os-parent` as the parent and `saas-os-core` as a dependency. Wait, these modules don't exist yet! We need to create them or adjust. Ah, wait, the instructions say "chore: finalize monorepo root" might be in another branch. Wait, looking at git log, there is no `saas-os-parent`!
