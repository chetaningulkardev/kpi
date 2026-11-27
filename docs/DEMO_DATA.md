# Development sample data

Start the application with `SPRING_PROFILES_ACTIVE=dev` (without `prod` active)
to create this fictional dataset. The seed is disabled whenever the `prod`
profile is active. All login addresses use the reserved `example.com` domain, no real
organization or employee data is used, and each sample account has the
password `Welcome2026!`. Use a separate development database: rerunning the
seed can advance sample KPI and scorecard workflow states. Existing scorecards
are not regenerated merely to fill missing sample scorecards.

The application requires a unique employee code, so fictional `KPI-` codes are
used instead of corporate identifiers.

| Name | Email | Role |
| --- | --- | --- |
| John Doe | `john.doe@example.com` | ADMIN |
| Maya Chen | `maya.chen@example.com` | MANAGER |
| Alex Rivera | `alex.rivera@example.com` | EMPLOYEE |
| Priya Nair | `priya.nair@example.com` | TRIBE_LEAD |
| Noah Bennett | `noah.bennett@example.com` | PORTFOLIO_LEAD |
| Leila Haddad | `leila.haddad@example.com` | SQUAD_LEAD |
| Ethan Park | `ethan.park@example.com` | TECH_FOCAL |
| Sofia Alvarez | `sofia.alvarez@example.com` | SENIOR_DEVELOPER |
| Owen Brooks | `owen.brooks@example.com` | MID_DEVELOPER |
| Ava Patel | `ava.patel@example.com` | JUNIOR_DEVELOPER |
| Daniel Kim | `daniel.kim@example.com` | QA_TECH_FOCAL |
| Grace Wilson | `grace.wilson@example.com` | QA_ENGINEER |
| Lucas Martin | `lucas.martin@example.com` | SCRUM_MASTER_TECH_FOCAL |
| Zoe Carter | `zoe.carter@example.com` | SCRUM_MASTER |
| Evelyn Hart | `evelyn.hart@example.com` | EMPLOYEE |
| Samir Shah | `samir.shah@example.com` | EMPLOYEE |
| Mia Turner | `mia.turner@example.com` | EMPLOYEE |
| Theo Green | `theo.green@example.com` | EMPLOYEE |

## Fictional organization

Three tribes—Customer Experience, Engineering Platforms, and Business
Operations—contain six portfolios: Digital Customer Journeys, Customer Care
Technology, Cloud Foundations, Developer Enablement, Workforce Insights, and
Enterprise Automation. Each portfolio contains two named squads. Six sample
teams, employee-manager relationships, leadership assignments, and role-based
KPI templates connect the organization end to end.

## KPI approval examples

The active cycle is **Q4 2026 Performance Review** (`FY26_Q4`). Each sample
role has an active template with three KPIs whose weights total 100%.
Generation creates KPI instances from those templates; sample self-assessments
and manager decisions then populate the review queue and audit history.

The active cycle contains examples at every workflow status:

| Status | What the sample demonstrates |
| --- | --- |
| `PENDING` | Generated KPI awaiting employee self-assessment |
| `SUBMITTED` | Employee submission awaiting manager action |
| `UNDER_REVIEW` | Manager has opened a submitted KPI |
| `REWORK_REQUIRED` | Manager feedback returned to the employee |
| `REJECTED` | Manager decision with an explanation |
| `APPROVED` | Manager decision with a rating |

At least one KPI follows the complete rework loop in its history:
`PENDING → SUBMITTED → REWORK_REQUIRED → SUBMITTED → APPROVED`. Submissions,
decisions, reviewer notes, and notifications are created through the same
workflow services used by the application. The sample admin can review any
employee's KPI; Maya Chen is the reporting manager for the sample team.

## Scorecards, calibration, and publication

One scorecard is generated for each of the 18 sample users. Alex Rivera, Owen
Brooks, Sofia Alvarez, Ethan Park, and Ava Patel each have fully approved KPI
sets so their weighted scores are complete. The cycle includes scorecards at
each relevant stage:

| Scorecard status | Sample account | What it demonstrates |
| --- | --- | --- |
| `DRAFT` | Alex Rivera | Generated scorecard awaiting review |
| `UNDER_REVIEW` | Owen Brooks | Manager review before calibration |
| `CALIBRATION` | Sofia Alvarez | Cross-team moderation in progress |
| `FINAL` | Ethan Park | Calibrated result awaiting release |
| `PUBLISHED` | Ava Patel | Released result visible in the employee's performance history |

Calibration notes, final scores, and calibrator identity are stored on the
scorecards. The sample cycle remains open for exploring employee assessments
and manager decisions; the published scorecard illustrates release without
closing that cycle.

## Start the sample app

Follow the [local development setup](../README.md#local-development) to start
PostgreSQL and configure its connection, then run the backend with `dev` active:

```sh
export JWT_SECRET="$(openssl rand -base64 64)"
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Sign in as John Doe at `john.doe@example.com` with password `Welcome2026!`.
The packaged frontend is served at <http://localhost:8080>; when running the
Angular development server separately, open <http://localhost:4200>.
