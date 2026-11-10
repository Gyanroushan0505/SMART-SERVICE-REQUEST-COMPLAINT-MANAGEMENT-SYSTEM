# Smart Complaint Management System – Changes Made

## Updated in this ZIP

1. `src/main/webapp/register.html`
   - Restyled the registration page with a responsive split layout.
   - Kept exactly the six requested fields: Full Name, Student ID, Email, Password, Floor, Room Number.
   - Kept the existing element IDs used by `app.js`.
   - Added password show/hide. No extra registration fields were added.

2. `src/main/webapp/style.css`
   - Added styles scoped to `.register-page`, so the other pages keep their existing styles.

3. `src/main/webapp/complaint-status.html`
   - Replaced the old three-step progress display with five status cards.
   - Added complaint summary, floor, room, priority, assigned staff name, resolution note, and resolved success panel.
   - Pending and Staff Assigned milestones use a green confirmed-state treatment; In Progress is highlighted as the active work state.
   - Added a clickable five-star feedback area and written feedback box.

4. `src/main/webapp/app.js`
   - Loads the complaint data into the new tracking layout.
   - Marks Pending, Assigned, In Progress, and Resolved steps from the database status.
   - Sends rating and written feedback to the existing feedback API.
   - Displays the assigned staff name in the Admin feedback table.

5. `src/main/webapp/admin.html` and `src/main/java/com/smartcomplaint/servlet/ApiServlet.java`
   - Added the assigned staff column to the Admin feedback list.
   - Admin feedback remains loaded from the MySQL feedback table and displays complaint ID, student, assigned staff, star rating, written comment, and submission date.

## Existing database structure

The current `schema.sql` already contains the `feedback` table with:
- `complaint_id`
- `user_id`
- `rating`
- `comments`
- `created_at`

It also already stores complaint status, assigned staff, floor, room, and resolution note. This update does not require adding a new table or changing the schema.

## Important data-model note

- The supplied `users` table has a role (`USER`, `ADMIN`, `STAFF`) but no separate staff designation/job-title column. The interface therefore shows the actual assigned staff name and does not invent a designation. Add a designation column only if the project owner later requests that database change.

## Verification notes

- JavaScript syntax check passed with `node --check`.
- Maven packaging could not be run in this environment because Maven is not installed here.
- Test the updated WAR on the same application server and database before submitting the project.

## Server compatibility warning

This project imports `jakarta.servlet.*` and uses Jakarta Servlet 6.0. It needs a compatible server such as Tomcat 10.1+. Tomcat 9 uses the older `javax.servlet.*` API and is not compatible with this project as-is.

## Database credential warning

`DB.java` currently contains a fallback database password in source code. For safety, set `DB_URL`, `DB_USER`, and `DB_PASSWORD` as environment variables and remove the hard-coded fallback before publishing the project.
