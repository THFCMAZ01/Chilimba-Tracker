 Project documentation
 
# Chilimba Tracker System
A digital, object-oriented financial tracking system designed to manage traditional Zambian rotating    savings and credit associations (Chilimbas).
## [rocket] Tech Stack *   **Backend:** Java 17, Javalin Framework, Maven *   **Frontend:** HTML5, CSS3, JavaScript (Vanilla) *   **Deployment:** Docker, Render Cloud 
## [people] Development Team (ZUT Software Engineering) *   Joshua Mazaza - Tech Lead & Core Models *   Gabriel Mwila - Documentation & Config *   Martin - Database Architecture *   Kondwani - Business Logic & Services *   Innocent - API Controllers
## [gear] Local Setup 1. Clone the repository: `git clone https://github.com/THFCMAZ01/Chilimba-Tracker.git` 2. Navigate to the project folder: `cd chilimba-tracker` 3. Compile and Run: `mvn clean compile exec:java` 4. Access the UI at `http://localhost:7070`

Server configuration

# Chilimba Tracker Application Properties
server.port=7070 
app.env=production 
app.name=ChilimbaTrackerAPI 
# Database Configuration (Pending Migration from HashMaps) db.url=jdbc:postgresql://localhost:5432/chilimba_db 
db.username=admin 
db.password=placeholder_password 
db.driver=org.postgresql.Driver

Commit workflow

git checkout main
git pull origin main
git checkout -b docs/gabriel-readme-config

APP_LINK: https://chilimba-tracker1.onrender.com/