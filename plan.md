SDU BAĞDAR — DEVELOPMENT PLAN

Project: SDU Bağdar — Smart University Platform for SDU Architecture: Microservices Backend: Java + Python (without application frameworks) Frontend: HTML, CSS, JavaScript Database: SQLite Communication: REST API (HTTP/JSON)

GOAL

Build a working MVP of SDU Bağdar that satisfies all CSS 217 Assignment 2 requirements and can later be extended into a portfolio-level project.

PHASE 1 — PROJECT SETUP

1. Initialize Git repository.
2. Create project structure according to AGENTS.md.
3. Configure Java and Python environments.
4. Set up three microservices: CampusPlus, FixMyCampus, ProjectForge.
5. Configure SQLite databases for each service.
6. Establish shared API conventions and error response formats.
7. Configure environment variables and service ports.
8. Create scripts to start all services.
9. Create initial README.

Result: All three services start independently and expose health-check endpoints.

PHASE 2 — SHARED FOUNDATION

1. Define Student and Teacher roles.
2. Implement user registration and login.
3. Implement password hashing and session authentication.
4. Implement role-based authorization.
5. Establish user identity across microservices.
6. Implement common request validation.
7. Implement standardized exception handling.
8. Implement database initialization and migration scripts.
9. Create reusable HTTP client utilities.

Result: Students and teachers can authenticate and access authorized endpoints.

PHASE 3 — CAMPUSPLUS MICROSERVICE

1. Create Room domain model.
2. Create Building domain model.
3. Create TeacherOffice domain model.
4. Create OfficeHour domain model.
5. Implement room database repositories.
6. Implement room listing and filtering.
7. Implement free-room search by date and time.
8. Implement teacher office directory.
9. Implement office-hours creation and management.
10. Implement student registration for office hours.
11. Prevent conflicting office-hour bookings.
12. Implement timetable import from a simulated external university system.

Design Pattern: Adapter

Create TimetableProvider interface. Create simulated SDU timetable component with an incompatible interface. Implement SDUTimetableAdapter. Demonstrate that CampusPlus works through the adapter without modifying the external component.

Result: Students find free rooms and teacher offices; teachers manage consultations.

PHASE 4 — FIXMYCAMPUS MICROSERVICE

1. Create Issue domain model.
2. Create IssueCategory domain model.
3. Create IssueReport domain model.
4. Implement issue repositories.
5. Implement issue submission.
6. Allow users to select buildings and rooms.
7. Add predefined issue categories.
8. Support custom issue descriptions.
9. Implement issue status tracking.
10. Implement duplicate issue detection using basic rules.
11. Implement issue confirmation and resolution.
12. Implement issue history.

Design Pattern: Decorator

Create IssueProcessor interface. Implement BasicIssueProcessor. Implement NotificationDecorator. Implement PriorityEscalationDecorator. Implement AuditLoggingDecorator. Allow decorators to be combined dynamically.

Result: Students and teachers report infrastructure problems and track their resolution.

PHASE 5 — PROJECTFORGE MICROSERVICE

1. Create Initiative domain model.
2. Create Club domain model.
3. Create Membership domain model.
4. Create Deadline domain model.
5. Implement initiative repositories.
6. Implement initiative creation.
7. Implement initiative categories.
8. Implement initiative search and filtering.
9. Implement initiative subscriptions.
10. Implement contact information for initiative organizers.
11. Implement club registration.
12. Implement club announcements and deadlines.
13. Implement joining and leaving initiatives.

Design Pattern: Strategy

Create InitiativeRecommendationStrategy interface. Implement CategoryBasedStrategy. Implement PopularityBasedStrategy. Implement RecentActivityStrategy. Allow recommendation strategy selection at runtime.

Result: Students and teachers publish initiatives, discover communities, and follow clubs.

PHASE 6 — MICROSERVICE INTEGRATION

1. Define service-to-service API contracts.
2. Implement Java-to-Python HTTP communication.
3. Implement shared user identity validation.
4. Integrate CampusPlus room information with FixMyCampus.
5. Allow FixMyCampus reports to reference CampusPlus rooms.
6. Implement safe handling of unavailable services.
7. Add HTTP timeouts and meaningful error responses.
8. Ensure services never directly access another service's database.
9. Implement integration tests.

Result: Three independent services operate as one application.

PHASE 7 — FRONTEND

1. Create login and registration pages.
2. Create shared navigation and dashboard.
3. Create CampusPlus room search page.
4. Create teacher directory.
5. Create office-hours management interface.
6. Create FixMyCampus issue submission form.
7. Create issue history and status pages.
8. Create ProjectForge initiative directory.
9. Create initiative creation form.
10. Create club directory and deadlines page.
11. Integrate frontend with backend APIs.
12. Implement loading, empty, and error states.
13. Add responsive design.

Result: Users can complete the main workflows through a browser.

PHASE 8 — TESTING

1. Test user registration and authentication.
2. Test room availability search.
3. Test office-hours creation and registration.
4. Test issue creation and persistence.
5. Test initiative creation and subscription.
6. Test Strategy Pattern implementations and runtime switching.
7. Test Adapter Pattern integration.
8. Test all three Decorator implementations.
9. Test combined decorators.
10. Test invalid input and authorization failures.
11. Test database persistence after service restart.
12. Test service communication failures.
13. Run complete end-to-end workflows.
14. Record actual test results.

Result: At least five functional tests plus pattern-specific tests pass.

PHASE 9 — DOCUMENTATION

1. Write Requirements Specification.
2. Define functional requirements.
3. Define non-functional requirements.
4. Document Student and Teacher roles.
5. Create use-case diagram.
6. Create UML class diagram.
7. Create microservices architecture diagram.
8. Create database ER diagrams.
9. Explain Strategy Pattern.
10. Explain Adapter Pattern.
11. Explain Decorator Pattern.
12. Document important architectural decisions.
13. Write testing documentation.
14. Write installation and execution instructions.
15. Prepare sample university data.
16. Update README.

Result: All mandatory assignment documentation is complete.

PHASE 10 — FINAL DELIVERY

1. Test project installation from scratch.
2. Verify all services start correctly.
3. Initialize databases using provided scripts.
4. Run all automated tests.
5. Verify main workflows.
6. Check design pattern demonstrations.
7. Verify documentation completeness.
8. Prepare project archive.
9. Prepare demonstration scenarios.
10. Submit before the assignment deadline.

Result: Complete Assignment 2 submission.

POST-MVP — PORTFOLIO DEVELOPMENT

1. Integrate actual SDU timetable data if access is approved.
2. Add room availability visualization.
3. Add campus occupancy prediction.
4. Add AI-assisted infrastructure report verification.
5. Add photo uploads and image analysis.
6. Implement advanced initiative recommendations.
7. Add notification delivery.
8. Introduce caching where measurements justify it.
9. Add service monitoring and structured logging.
10. Add Docker deployment and CI/CD.
11. Implement security hardening.
12. Conduct performance and load testing.

PRIORITY RULES

P0 — Mandatory for Assignment 2: Three working design patterns, persistent data, core workflows, validation, layered architecture, tests, and documentation.

P1 — Important for SDU Bağdar MVP: Authentication, three connected services, functional frontend, room search, office hours, issue reporting, initiatives, and clubs.

P2 — Optional enhancements: AI verification, crowd prediction, advanced recommendations, real university integrations, and analytics.

DEVELOPMENT PRINCIPLE

Every feature must follow the same implementation sequence:

Requirement → Domain Model → Repository → Business Logic → API → Frontend → Tests → Documentation.

Do not implement optional features before the core workflows and mandatory design patterns are complete.

FIRST ACTION

Obtain instructor approval for SDU Bağdar as a custom project topic, then implement Phase 1 and the minimum end-to-end functionality required for Assignment 2.
