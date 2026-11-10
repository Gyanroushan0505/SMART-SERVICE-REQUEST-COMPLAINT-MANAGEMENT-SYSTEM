/* =====================================================
   1. BASIC SETTINGS AND HELPER FUNCTIONS
   ===================================================== */

const API = 'api';
const $ = (id) => document.getElementById(id);

const esc = (value) =>
    String(value ?? '').replace(/[&<>"']/g, (char) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[char]));

async function request(path, options = {}) {
    const response = await fetch(`${API}/${path}`, {
        credentials: 'same-origin',
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });

    const data = await response.json().catch(() => ({
        error: 'Unexpected server response'
    }));

    if (!response.ok) {
        // Admin session expire ho to admin login par bhejo.
        if (
            response.status === 401 &&
            !location.pathname.endsWith('html.html') &&
            !location.pathname.endsWith('register.html') &&
            !location.pathname.endsWith('admin-login.html')
        ) {
            if (location.pathname.endsWith('admin.html')) {
                window.location.replace('admin-login.html');
            } else {
                window.location.replace('html.html');
            }
        }

        throw new Error(data.error || 'Request failed');
    }

    return data;
}

function message(text, good = false, target = 'formMessage') {
    const element = $(target);

    if (element) {
        element.textContent = text;
        element.style.color = good ? '#15803d' : '#b91c1c';
    } else {
        alert(text);
    }
}


/* =====================================================
   2. LOGOUT
   Student -> html.html
   Admin   -> admin-login.html
   ===================================================== */


async function logout() {
    try {
        await request('logout', {
            method: 'POST',
            body: '{}'
        });
    } catch (error) {
        console.error('Logout request failed:', error);
    }

    window.location.replace('admin-login.html');
}



   


/* =====================================================
   3. DATE AND COMPLAINT TABLE HELPERS
   ===================================================== */

function dateText(value) {
    if (!value) return '—';

    const date = new Date(value);

    return isNaN(date)
        ? String(value)
        : date.toLocaleDateString();
}

function statusLink(complaint) {
    return `<a href="complaint-status.html?id=${encodeURIComponent(
        complaint.publicId
    )}">View</a>`;
}

function rowCells(complaint, includeLink = false) {
    return `<tr>
        <td>${esc(complaint.publicId)}</td>
        <td>${esc(complaint.category)}</td>
        <td>${esc(complaint.roomNumber)}</td>
        <td>${esc(dateText(complaint.createdAt))}</td>
        <td>${esc(complaint.priority)}</td>
        <td>${esc(complaint.status)}</td>
        ${includeLink ? `<td>${statusLink(complaint)}</td>` : ''}
    </tr>`;
}


/* =====================================================
   4. LOGGED-IN USER DETAILS
   ===================================================== */

async function loadUser() {
    const user = await request('me');
    const name = user.name || user.studentId;

    document.querySelectorAll('.student-info, .user')
        .forEach(element => {
            element.textContent = name;
        });

    if ($('studentId')) {
        $('studentId').value = user.studentId || '';
    }

    if ($('profileStudentId')) {
        $('profileStudentId').value = user.studentId || '';
    }

    if ($('profileName')) {
        $('profileName').value = user.name || '';
    }

    if ($('profileEmail')) {
        $('profileEmail').value = user.email || '';
    }

    if ($('profileFloor')) {
        $('profileFloor').value = user.floor || '';
    }

    if ($('profileRoom')) {
        $('profileRoom').value = user.roomNumber || '';
    }

    return user;
}


/* =====================================================
   5. LOAD COMPLAINTS AND COMPLAINT STATUS
   ===================================================== */

async function loadComplaints() {
    const complaints = await request('complaints');

    const totalEl = $('totalComplaints');
    const pendingEl = $('pendingComplaints');
    const progressEl = $('progressComplaints');
    const resolvedEl = $('resolvedComplaints');

    if (totalEl) {
        totalEl.textContent = complaints.length;
    }

    if (pendingEl) {
        pendingEl.textContent = complaints.filter(
            c => c.status === 'Pending'
        ).length;
    }

    if (progressEl) {
        progressEl.textContent = complaints.filter(
            c => c.status === 'In Progress'
        ).length;
    }

    if (resolvedEl) {
        resolvedEl.textContent = complaints.filter(
            c => c.status === 'Resolved'
        ).length;
    }

    document.querySelectorAll('tbody').forEach(tableBody => {
        if (
            tableBody.id === 'adminComplaints' ||
            tableBody.id === 'staffComplaints'
        ) {
            return;
        }

        const table = tableBody.closest('table');
        if (!table) return;

        const title = (document.title || '').toLowerCase();

        if (
            title.includes('my complaints') ||
            table.closest('.my-complaints-section')
        ) {
            tableBody.innerHTML = complaints.length
                ? complaints.map(c => rowCells(c, true)).join('')
                : '<tr><td colspan="7">No complaints yet.</td></tr>';

        } else if (title.includes('history')) {
            tableBody.innerHTML = complaints.length
                ? complaints.map(c => rowCells(c, true)).join('')
                : '<tr><td colspan="7">No complaint history yet.</td></tr>';

        } else if (title.startsWith('home')) {
            tableBody.innerHTML = complaints.length
                ? complaints.slice(0, 5).map(c => `
                    <tr>
                        <td>${esc(c.publicId)}</td>
                        <td>${esc(c.category)}</td>
                        <td>${esc(c.roomNumber)}</td>
                        <td>${esc(c.status)}</td>
                    </tr>
                `).join('')
                : '<tr><td colspan="4">No complaints yet.</td></tr>';
        }
    });

    const statusId = new URLSearchParams(location.search).get('id');

    if (!location.pathname.endsWith('complaint-status.html')) {
        return;
    }

    const complaint = complaints.find(c => c.publicId === statusId);

    if (!complaint) {
        if ($('statusMessage')) {
            $('statusMessage').textContent =
                'Complaint not found. Please check the complaint ID.';
        }
        return;
    }

    const setText = (id, value) => {
        if ($(id)) $(id).textContent = value || '—';
    };

    setText('statusComplaintId', complaint.publicId);
    setText('statusCategory', complaint.category);
    setText('statusPriority', complaint.priority);
    setText('statusFloor', complaint.floor);
    setText('statusRoom', complaint.roomNumber);
    setText('statusValue', complaint.status);
    setText('statusDescription', complaint.description);
    setText('submittedDate', dateText(complaint.createdAt));
    setText('lastUpdated', dateText(complaint.updatedAt));
    setText('resolutionNote', complaint.resolutionNote || 'No resolution note yet.');
    setText('statusStaffName', complaint.assignedStaff || 'Staff not assigned yet');
    setText('statusStaffRole', complaint.assignedStaff ? 'Assigned to this complaint' : 'Staff details will appear after assignment.');

    if ($('statusMessage')) {
        $('statusMessage').textContent =
            `Track the progress and updates for ${complaint.publicId}.`;
    }

    const normalizedStatus = String(complaint.status || 'Pending').toLowerCase();
    const stepIds = ['stepSubmitted', 'stepPending', 'stepAssigned', 'stepProgress', 'stepResolved'];
    const steps = stepIds.map(id => $(id)).filter(Boolean);
    const stepIndex = normalizedStatus === 'resolved' ? 4
        : normalizedStatus === 'in progress' ? 3
        : normalizedStatus === 'assigned' ? 2
        : normalizedStatus === 'rejected' ? 1 : 1;

    steps.forEach((step, index) => {
        step.classList.remove('done', 'current');
        const state = step.querySelector('.tracking-step-state');
        if (index < stepIndex || normalizedStatus === 'resolved') {
            step.classList.add('done');
            if (state) state.textContent = 'Completed';
        } else if (index === stepIndex && normalizedStatus !== 'resolved') {
            step.classList.add('current');
            if (state) state.textContent = normalizedStatus === 'rejected' ? 'Rejected' : 'Current';
        } else if (state) {
            state.textContent = 'Waiting';
        }
    });

    // Pending and Staff Assigned are highlighted in green when they are the latest confirmed milestone.
    if (normalizedStatus === 'pending' && $('stepPending')) {
        $('stepPending').classList.add('pending-current');
    }
    if (normalizedStatus === 'assigned' && $('stepAssigned')) {
        $('stepAssigned').classList.add('assigned-current');
    }

    // Complaint submitted is always the first completed step.
    if (steps[0]) {
        steps[0].classList.add('done');
        const state = steps[0].querySelector('.tracking-step-state');
        if (state) state.textContent = 'Completed';
    }

    if (normalizedStatus === 'assigned' || normalizedStatus === 'in progress' || normalizedStatus === 'resolved') {
        const pendingStep = $('stepPending');
        if (pendingStep) {
            pendingStep.classList.add('done');
            pendingStep.classList.remove('current');
            const state = pendingStep.querySelector('.tracking-step-state');
            if (state) state.textContent = 'Completed';
        }
    }

    if (normalizedStatus === 'in progress' || normalizedStatus === 'resolved') {
        const assignedStep = $('stepAssigned');
        if (assignedStep) {
            assignedStep.classList.add('done');
            assignedStep.classList.remove('current');
            const state = assignedStep.querySelector('.tracking-step-state');
            if (state) state.textContent = 'Completed';
        }
        setText('assignedStepNote', complaint.assignedStaff || 'Staff assigned');
    }

    if (normalizedStatus === 'resolved') {
        const progressStep = $('stepProgress');
        if (progressStep) {
            progressStep.classList.add('done');
            progressStep.classList.remove('current');
            const state = progressStep.querySelector('.tracking-step-state');
            if (state) state.textContent = 'Completed';
        }
        const badge = $('statusBadge');
        if (badge) {
            badge.textContent = 'Resolved';
            badge.classList.add('resolved');
        }
        if ($('resolvedSuccess')) $('resolvedSuccess').hidden = false;
        setText('resolvedDate', `Resolved on ${dateText(complaint.updatedAt)}`);
        if ($('feedbackSection')) $('feedbackSection').hidden = false;
    } else {
        const badge = $('statusBadge');
        if (badge) badge.textContent = complaint.status || 'Pending';
    }

    if ($('progressCount')) {
        const completed = normalizedStatus === 'resolved' ? 5
            : normalizedStatus === 'in progress' ? 3
            : normalizedStatus === 'assigned' ? 2 : 1;
        $('progressCount').textContent = `${completed} / 5 completed`;
    }

    if ($('staffAssignmentBadge')) {
        const assigned = Boolean(complaint.assignedStaff);
        $('staffAssignmentBadge').textContent = assigned ? 'Assigned' : 'Not assigned';
        $('staffAssignmentBadge').classList.toggle('is-assigned', assigned);
    }

    // Clickable star rating for student feedback.
    let selectedRating = 0;
    const starButtons = document.querySelectorAll('#feedbackStars button');
    const ratingLabels = ['', 'Poor', 'Fair', 'Good', 'Very good', 'Excellent'];
    starButtons.forEach(button => {
        button.addEventListener('click', () => {
            selectedRating = Number(button.dataset.rating);
            starButtons.forEach(star => {
                star.classList.toggle('selected', Number(star.dataset.rating) <= selectedRating);
                star.setAttribute('aria-checked', Number(star.dataset.rating) === selectedRating ? 'true' : 'false');
            });
            setText('ratingCaption', `${selectedRating} / 5 — ${ratingLabels[selectedRating]}`);
            if ($('feedbackBtn')) $('feedbackBtn').disabled = !($('feedbackComment')?.value.trim());
        });
    });

    if ($('feedbackComment')) {
        $('feedbackComment').addEventListener('input', () => {
            if ($('feedbackBtn')) $('feedbackBtn').disabled = !(selectedRating > 0 && $('feedbackComment').value.trim());
        });
    }

    if ($('feedbackBtn')) {
        $('feedbackBtn').onclick = async () => {
            const comment = $('feedbackComment')?.value.trim() || '';
            if (!selectedRating) {
                setText('feedbackMessage', 'Please select a star rating.');
                return;
            }
            if (!comment) {
                setText('feedbackMessage', 'Please write your feedback.');
                return;
            }
            try {
                $('feedbackBtn').disabled = true;
                await request(`complaints/${encodeURIComponent(complaint.publicId)}/feedback`, {
                    method: 'POST',
                    body: JSON.stringify({ rating: selectedRating, comments: comment })
                });
                setText('feedbackMessage', 'Thank you! Your feedback has been saved.');
                $('feedbackBtn').textContent = 'Feedback Submitted';
            } catch (error) {
                setText('feedbackMessage', error.message);
                $('feedbackBtn').disabled = false;
            }
        };
    }
}


/* =====================================================
   6. STUDENT / STAFF LOGIN
   ===================================================== */

async function loginSubmit(event) {
    event.preventDefault();

    try {
        const data = await request('login', {
            method: 'POST',
            body: JSON.stringify({
                studentId: $('userid').value.trim(),
                password: $('password').value
            })
        });

        if (!data.redirect) {
            throw new Error('Login response did not contain a redirect.');
        }

        location.href = data.redirect;
    } catch (error) {
        message(error.message);
    }
}


/* =====================================================
   7. ADMIN LOGIN
   ===================================================== */

async function adminLoginSubmit(event) {
    event.preventDefault();

    const button = document.querySelector(
        '#adminLoginForm button[type="submit"]'
    );

    try {
        if (button) button.disabled = true;

        message('Checking admin credentials...', true);

        const data = await request('login', {
            method: 'POST',
            body: JSON.stringify({
                studentId: $('userid').value.trim(),
                password: $('password').value,
                requestedRole: 'ADMIN'
            })
        });

        if (!data.redirect || data.redirect !== 'admin.html') {
            throw new Error(
                'Admin login failed or this account is not an admin.'
            );
        }

        window.location.href = 'admin.html';
    } catch (error) {
        message(error.message);
    } finally {
        if (button) button.disabled = false;
    }
}


/* =====================================================
   8. STUDENT REGISTRATION
   ===================================================== */

async function registerSubmit(event) {
    event.preventDefault();

    try {
        const data = await request('register', {
            method: 'POST',
            body: JSON.stringify({
                name: $('regName').value.trim(),
                studentId: $('regStudentId').value.trim(),
                email: $('regEmail').value.trim(),
                password: $('regPassword').value,
                floor: $('regFloor').value,
                roomNumber: $('regRoom').value.trim()
            })
        });

        location.href = data.redirect || 'home.html';
    } catch (error) {
        message(error.message);
    }
}


/* =====================================================
   9. ADMIN CREATES STAFF ACCOUNT
   ===================================================== */

async function createStaffSubmit(event) {
    event.preventDefault();

    try {
        const data = await request('admin/staff', {
            method: 'POST',
            body: JSON.stringify({
                name: $('staffName').value.trim(),
                studentId: $('staffId').value.trim(),
                email: $('staffEmail').value.trim(),
                password: $('staffPassword').value,
                floor: $('staffFloor')
                    ? $('staffFloor').value.trim()
                    : ''
            })
        });

        message(
            data.message || 'Staff account created successfully!',
            true,
            'staffFormMessage'
        );

        $('createStaffForm').reset();
        await loadAdmin();

    } catch (error) {
        message(error.message, false, 'staffFormMessage');
    }
}


/* =====================================================
   10. SUBMIT A COMPLAINT
   ===================================================== */

async function complaintSubmit(event) {
    event.preventDefault();

    try {
        const data = await request('complaints', {
            method: 'POST',
            body: JSON.stringify({
                floor: $('floor').value,
                roomNumber: $('roomNumber').value.trim(),
                category: $('category').value,
                description: $('description').value.trim()
            })
        });

        alert(
            `Complaint submitted: ${data.publicId}\n` +
            `ML category: ${data.prediction.category}\n` +
            `Predicted priority: ${data.prediction.priority}\n` +
            `Expected resolution: ${data.prediction.expectedDays} day(s)`
        );

        location.href =
            `complaint-status.html?id=${encodeURIComponent(data.publicId)}`;

    } catch (error) {
        message(error.message);
    }
}


/* =====================================================
   11. ADMIN DASHBOARD
   ===================================================== */

async function loadAdmin() {
    let complaints = [];
    let staff = [];
    let feedback = [];
    let complaintError = '';
    let staffError = '';
    let feedbackError = '';

    try {
        complaints = await request('admin/complaints');
    } catch (error) {
        complaintError = error.message || 'Could not load complaints.';
    }

    try {
        staff = await request('staff');
    } catch (error) {
        staffError = error.message || 'Could not load staff.';
    }

    // Always refresh feedback independently from complaints and staff.
    if ($('adminFeedback')) {
        $('adminFeedback').innerHTML =
            '<tr><td colspan="6">Loading feedback...</td></tr>';
        try {
            feedback = await request('admin/feedback');
            $('adminFeedback').innerHTML = feedback.length
                ? feedback.map(f => {
                    const rating = Math.max(0, Math.min(5, Number(f.rating) || 0));
                    return `
                    <tr>
                        <td>${esc(f.complaintId || 'Complaint unavailable')}</td>
                        <td>
                            <strong>${esc(f.studentName || 'Student')}</strong><br>
                            <small>${esc(f.studentId || '')}</small>
                        </td>
                        <td>${esc(f.assignedStaff || 'Not assigned')}</td>
                        <td>
                            <span class="feedback-rating">${'★'.repeat(rating)}${'☆'.repeat(5 - rating)}</span><br>
                            <small>${rating} / 5</small>
                        </td>
                        <td class="feedback-comment">${esc((f.comments || '').trim() || 'No written comment')}</td>
                        <td>${esc(dateText(f.createdAt))}</td>
                    </tr>`;
                }).join('')
                : '<tr><td colspan="6">No user feedback submitted yet. Feedback appears here after a student submits it for a resolved complaint.</td></tr>';
        } catch (error) {
            console.error('Admin feedback loading failed:', error);
            $('adminFeedback').innerHTML =
                `<tr><td colspan="6">Feedback could not be loaded: ${esc(error.message || 'Unknown error')}. Check admin login and Tomcat server.</td></tr>`;
        }
    }

    if ($('totalCount')) {
        $('totalCount').textContent =
            complaintError ? '!' : complaints.length;
    }

    if ($('pendingCount')) {
        $('pendingCount').textContent = complaintError
            ? '!'
            : complaints.filter(
                c => c.status === 'Pending' || c.status === 'Assigned'
            ).length;
    }

    if ($('progressCount')) {
        $('progressCount').textContent = complaintError
            ? '!'
            : complaints.filter(
                c => c.status === 'In Progress'
            ).length;
    }

    if ($('resolvedCount')) {
        $('resolvedCount').textContent = complaintError
            ? '!'
            : complaints.filter(
                c => c.status === 'Resolved'
            ).length;
    }

    const staffOptions = staff.map(s => `
        <option value="${esc(s.id)}">
            ${esc(s.name)} (${esc(s.studentId)})
        </option>
    `).join('');

    if ($('staffList')) {
        $('staffList').innerHTML = staffError
            ? `<p class="error-message">Could not load staff: ${esc(staffError)}</p>`
            : (
                staff.length
                    ? staff.map(s => `
                        <div class="staff-list-item">
                            <strong>${esc(s.name)}</strong> —
                            ${esc(s.studentId)} —
                            ${esc(s.email)}
                        </div>
                    `).join('')
                    : 'No staff accounts yet. Use the form above to create the first staff account.'
            );
    }

    if (!$('adminComplaints')) return;

    if (complaintError) {
        $('adminComplaints').innerHTML = `
            <tr>
                <td colspan="8">
                    Could not load complaints: ${esc(complaintError)}.
                    Check login session, database connection and Tomcat logs.
                </td>
            </tr>
        `;
        return;
    }

    $('adminComplaints').innerHTML = complaints.length
        ? complaints.map(c => `
            <tr>
                <td>${esc(c.publicId)}</td>
                <td>
                    ${esc(c.name)}<br>
                    ${esc(c.studentId)}
                </td>
                <td>
                    <strong>${esc(c.category)}</strong><br>
                    ${esc(c.description)}
                </td>
                <td><span class="priority-badge priority-${esc(String(c.predictedPriority || c.priority || 'Medium').toLowerCase())}">${esc(c.predictedPriority || c.priority || 'Medium')}</span></td>
                <td>
                    <strong>${esc(c.predictedCategory || '—')}</strong><br>
                    <span class="ml-prediction-label">ML predicted priority: ${esc(c.predictedPriority || 'Not available')}</span><br>
                    <small>Estimated: ${esc(c.expectedDays || 3)} day(s)</small>
                </td>
                <td>
                    <select id="st-${esc(c.id)}">
                        ${[
                            'Pending',
                            'Assigned',
                            'In Progress',
                            'Resolved',
                            'Rejected'
                        ].map(status => `
                            <option value="${esc(status)}"
                                ${c.status === status ? 'selected' : ''}>
                                ${esc(status)}
                            </option>
                        `).join('')}
                    </select>

                    <input id="note-${esc(c.id)}"
                        placeholder="Resolution note"
                        value="${esc(c.resolutionNote || '')}">
                </td>
                <td>
                    <select id="assign-${esc(c.id)}">
                        <option value="">Select staff</option>
                        ${staffOptions}
                    </select>

                    <button type="button"
                        onclick="assignStaff('${esc(c.publicId)}', ${Number(c.id)})">
                        Assign
                    </button>
                </td>
                <td>
                    <button type="button"
                        onclick="updateAdmin('${esc(c.publicId)}', ${Number(c.id)})">
                        Save status
                    </button>
                </td>
            </tr>
        `).join('')
        : '<tr><td colspan="8">No complaints have been submitted.</td></tr>';

    // Pehle se assigned staff ko dropdown mein select karo.
    complaints.forEach(c => {
        const select = $(`assign-${c.id}`);

        if (select && c.assignedStaffId) {
            select.value = String(c.assignedStaffId);
        }
    });
}


/* =====================================================
   12. ASSIGN STAFF TO COMPLAINT
   ===================================================== */

async function assignStaff(publicId, id) {
    const staffId = $(`assign-${id}`)?.value;

    if (!staffId) {
        alert('Please select a staff member first.');
        return;
    }

    try {
        await request(
            `admin/complaints/${encodeURIComponent(publicId)}/assign`,
            {
                method: 'PATCH',
                body: JSON.stringify({
                    staffId: Number(staffId)
                })
            }
        );

        alert('Staff assigned successfully.');
        await loadAdmin();

    } catch (error) {
        alert(error.message);
    }
}


/* =====================================================
   13. ADMIN UPDATES COMPLAINT STATUS
   ===================================================== */

async function updateAdmin(publicId, id) {
    try {
        await request(
            `admin/complaints/${encodeURIComponent(publicId)}`,
            {
                method: 'PATCH',
                body: JSON.stringify({
                    status: $(`st-${id}`).value,
                    note: $(`note-${id}`).value
                })
            }
        );

        alert('Status updated successfully.');
        await loadAdmin();

    } catch (error) {
        alert(error.message);
    }
}


/* =====================================================
   14. STAFF DASHBOARD
   ===================================================== */

async function loadStaff() {
    const complaints = await request('staff/complaints');

    if (!$('staffComplaints')) return;

    $('staffComplaints').innerHTML = complaints.length
        ? complaints.map(c => `
            <tr>
                <td>${esc(c.publicId)}</td>
                <td>
                    ${esc(c.name)}<br>
                    ${esc(c.floor)} / ${esc(c.roomNumber)}
                </td>
                <td>
                    ${esc(c.category)}<br>
                    ${esc(c.description)}
                </td>
                <td>${esc(c.priority)}</td>
                <td>${esc(c.status)}</td>
                <td>
                    <select id="staff-st-${esc(c.id)}">
                        <option value="In Progress"
                            ${c.status === 'In Progress' ? 'selected' : ''}>
                            In Progress
                        </option>
                        <option value="Resolved"
                            ${c.status === 'Resolved' ? 'selected' : ''}>
                            Resolved
                        </option>
                    </select>
                </td>
                <td>
                    <input id="staff-note-${esc(c.id)}"
                        value="${esc(c.resolutionNote || '')}"
                        placeholder="Work done / note">
                </td>
                <td>
                    <button type="button"
                        onclick="updateStaff('${esc(c.publicId)}', ${Number(c.id)})">
                        Save
                    </button>
                </td>
            </tr>
        `).join('')
        : '<tr><td colspan="8">No complaints assigned to you.</td></tr>';
}


/* =====================================================
   15. STAFF UPDATES COMPLAINT STATUS
   ===================================================== */

async function updateStaff(publicId, id) {
    try {
        await request(
            `staff/complaints/${encodeURIComponent(publicId)}`,
            {
                method: 'PATCH',
                body: JSON.stringify({
                    status: $(`staff-st-${id}`).value,
                    note: $(`staff-note-${id}`).value
                })
            }
        );

        alert('Progress updated successfully.');
        await loadStaff();

    } catch (error) {
        alert(error.message);
    }
}


/* =====================================================
   16. INITIALIZE THE PAGE
   ===================================================== */

document.addEventListener('DOMContentLoaded', async () => {
    // Student login form
    if ($('loginForm')) {
        $('loginForm').addEventListener('submit', loginSubmit);
    }

    // Separate admin login form
    if ($('adminLoginForm')) {
        $('adminLoginForm').addEventListener(
            'submit',
            adminLoginSubmit
        );
    }

    // Admin creates staff
    if ($('createStaffForm')) {
        $('createStaffForm').addEventListener(
            'submit',
            createStaffSubmit
        );
    }

    // Student registration
    if ($('registerForm')) {
        $('registerForm').addEventListener(
            'submit',
            registerSubmit
        );
    }

    // Complaint submission
    if ($('complaintForm')) {
        $('complaintForm').addEventListener(
            'submit',
            complaintSubmit
        );
    }

    // Login/register pages par dashboard session check nahi karna.
    if (
        location.pathname.endsWith('html.html') ||
        location.pathname.endsWith('register.html') ||
        location.pathname.endsWith('admin-login.html')
    ) {
        return;
    }

    try {
        const user = await loadUser();

        // Admin page sirf ADMIN role ke liye.
        if (
            location.pathname.endsWith('admin.html') &&
            user.role !== 'ADMIN'
        ) {
            window.location.replace('admin-login.html');
            return;
        }

        // Staff page sirf STAFF role ke liye.
        if (
            location.pathname.endsWith('staff.html') &&
            user.role !== 'STAFF'
        ) {
            window.location.replace('html.html');
            return;
        }

        // Page ke hisaab se data load karo.
        if (location.pathname.endsWith('admin.html')) {
            await loadAdmin();

        } else if (location.pathname.endsWith('staff.html')) {
            await loadStaff();

        } else {
            await loadComplaints();
        }

    } catch (error) {
        console.error('Page initialization failed:', error);

        // Admin session/data error.
        if ($('adminComplaints')) {
            $('adminComplaints').innerHTML = `
                <tr>
                    <td colspan="8">
                        Could not load admin data: ${esc(error.message)}.
                        Please login again or check server.
                    </td>
                </tr>
            `;
        }

        // Staff data error.
        if ($('staffList')) {
            $('staffList').textContent =
                `Could not load staff: ${error.message}`;
        }
    }
});