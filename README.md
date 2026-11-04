# Chilimba Tracker
Chilimba Tracker is a digital financial tracking system designed to help manage traditional Zambian rotating savings and credit associations (Chilimbas).
The application supports member registration, contribution recording, payout processing, and financial activity tracking.
Project Status
This project is currently being prepared for a group demonstration.
The core member, contribution, and payout workflows have been implemented and tested. Authentication and role-based access control are still planned improvements.
Important: This version is for demonstration and testing only. Do not use real savings or expose the application publicly for handling real financial transactions. Financial endpoints do not yet require authentication.
Technology Stack
Backend: Java 17, Javalin 6
Frontend: HTML5, CSS3, JavaScript
Database: Neon PostgreSQL
Build tool: Maven Wrapper
Deployment: Docker and Render
Testing: JUnit 5
Development Team
ZUT Software Engineering Team:
Joshua Mazaza - Tech Lead and Core Models
Gabriel Mwila - Documentation and Configuration
Martin - Database Architecture
Kondwani - Business Logic and Services
Innocent - API Controllers
Group Members
#
Full Name
Student Number
Role
1
Joshua Mazaza
2410123
Tech Lead and Core Models
2
Gabriel Mwila
2410234
Documentation and Configuration
3
Martin Muloshi 
2410009
Database Architecture
4
Kondwani Mwelwa 
2410395
Business Logic and Services
5
Innocent Kabwe
2410109
API Controllers
Requirements
Before running the application, install:
Java Development Kit (JDK) 17 or later.
Git.
An internet connection for the initial Maven Wrapper and dependency downloads.
You do not need to install Maven separately. The project includes the Maven Wrapper.
You do not need to install PostgreSQL locally. The team uses one shared Neon PostgreSQL database.
Getting Started
1. Clone the repository
git clone https://github.com/THFCMAZ01/Chilimba-Tracker.git
cd Chilimba-Tracker
2. Open the Maven project directory
cd chilimba-tracker
Run all subsequent application commands from this directory.
3. Configure the database connection
Each team member must create their own local .env file.
Windows PowerShell:
Copy-Item .env.example .env
Windows Command Prompt:
copy .env.example .env
Linux:
cp .env.example .env
Open .env in a text editor and replace the placeholders with the database connection details provided privately by the project maintainer.
The file must contain these three settings:
DB_URL=jdbc:postgresql://YOUR_HOSTNAME/neondb?sslmode=require
DB_USERNAME=YOUR_DATABASE_USERNAME
DB_PASSWORD=YOUR_DATABASE_PASSWORD
The team uses one shared Neon database. All members connect to the same database, so changes made by one member will be visible to everyone.
Security rules:
Never commit or upload your .env file.
Never share database credentials in GitHub commits or public messages.
Do not replace the placeholders in .env.example with real credentials.
Keep the shared database credentials private.
4. Run the automated tests
Run from the chilimba-tracker directory.
Windows PowerShell or Command Prompt:
.\mvnw.cmd test
Linux:
./mvnw test
The first run may download Maven and project dependencies.
5. Start the application
Windows PowerShell:
.\mvnw.cmd exec:java "-Dexec.mainClass=zm.zut.Main"
Windows Command Prompt:
mvnw.cmd exec:java "-Dexec.mainClass=zm.zut.Main"
Linux:
./mvnw exec:java -Dexec.mainClass=zm.zut.Main
When the server starts, open the application in your browser:
http://localhost:7070
To stop the server, press Ctrl + C in the terminal running the application.
Shared Database Guidelines
All team members connect to the same Neon PostgreSQL database.
This means:
Members registered by one team member will appear for everyone.
Contributions recorded by one team member will be visible to everyone.
Payout operations affect the shared database.
Team members must coordinate before testing operations that modify financial records.
Use clearly identifiable test records during development and demonstrations.
Avoid executing payout operations repeatedly. A successful payout closes the current contribution cycle and creates another one.
Application Features
Member registration and management.
Contribution recording.
Contribution tracking for the current cycle.
Rotational payout processing.
Payout history.
Financial activity ledger.
Dashboard summaries.
API Endpoints
Method
Endpoint
Description
GET
/api/status
Application and database status
GET
/members
Retrieve members
POST
/members
Register a member
GET
/contributions
Retrieve current-cycle contributions
POST
/contributions
Record a contribution
GET
/payouts
Retrieve payout history
POST
/payouts
Execute a payout
The application serves its frontend and API from the same server.
Project Structure
Chilimba-Tracker/
├── .gitignore
├── .dockerignore
├── README.md
└── chilimba-tracker/
    ├── .mvn/
    │   └── wrapper/
    ├── mvnw
    ├── mvnw.cmd
    ├── .env.example
    ├── Dockerfile
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/zm/zut/
        │   └── resources/public/
        └── test/
            └── java/zm/zut/
Classes
All classes are in chilimba-tracker/src/main/java/zm/zut/.
Class
Responsibility
Main
Starts the Javalin server, defines the REST endpoints and handles exceptions.
User
Abstract parent class for people in the group. Holds private fields (id, member code, name, phone, join date) with getters and setters.
OrdinaryMember
Extends User. A normal member who can only view records.
Treasurer
Extends User. Can manage funds (record contributions and payouts).
Contribution
Holds the details of one contribution made by a member.
Payout
Holds the details of one payout made to a member.
PayoutRule
Interface that decides who receives the next payout.
RotationPayout
Implements PayoutRule. Picks recipients in registration order and starts again once everyone has been paid.
SavingsGroup
Applies the business rules: every member must contribute before the next member is paid.
MissedContributionException
Custom exception thrown when a payout is attempted while contributions are missing. Handled in Main and returned as a 400 error.
MemberRepository
Saves and retrieves members in the database.
ContributionRepository
Saves contributions and retrieves those in the current cycle.
CycleRepository
Finds the current open contribution cycle, or creates the next one.
PayoutRepository
Executes payouts, closes the cycle and returns payout history.
Database
Reads the connection settings from .env or environment variables and connects to the database.
Deployment
The application is configured for Docker deployment on Render.
Demo application: https://chilimba-tracker1.onrender.com/
The deployed service requires these environment variables to be configured in Render:
DB_URL
DB_USERNAME
DB_PASSWORD
Use the authorized Neon database connection details. Never put their actual values in this README.
Deployment and live database access should be coordinated by the project maintainer.
Current Limitations and Future Improvements
The following improvements remain important:
User authentication and session management.
Treasurer-only permissions for financial operations.
Protection of payout execution.
Stronger authorization and financial transaction safeguards.
Additional integration and user-interface testing.
Improved production deployment and monitoring.
Until authentication and authorization are implemented, the application should only be used with test data.
Team Collaboration
Pull the latest changes before starting work.
Create a separate Git branch for your task.
Test changes locally before sharing them.
Coordinate database testing with the rest of the team.
Never commit environment files, credentials, or other secrets.
Avoid making uncoordinated changes to shared financial records.