// LOGIN
const loginForm = document.getElementById("loginForm");

if (loginForm) {
    loginForm.addEventListener("submit", function(event) {
        event.preventDefault();

        const studentId = document.getElementById("userid").value;
        const password = document.getElementById("password").value;

        if (studentId === "" || password === "") {
            alert("Please enter Student ID and Password.");
        } else {
            alert("Login Successful! ✅");
            window.location.href = "home.html";
        }
    });
}


// SUBMIT COMPLAINT
const complaintForm = document.getElementById("complaintForm");

if (complaintForm) {
    complaintForm.addEventListener("submit", function(event) {
        event.preventDefault();

        alert("Complaint Submitted Successfully! ✅");

        complaintForm.reset();
    });
}
function showPassword() {
    const password = document.getElementById("password");
    const button = document.querySelector(".show-password");

    if (password.type === "password") {
        password.type = "text";
        button.textContent = "🙈";
    } else {
        password.type = "password";
        button.textContent = "👁️";
    }
}