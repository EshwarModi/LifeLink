/**
 * LifeLink Client Script
 * Provides CSRF helpers for fetch() calls and dynamic form interactions.
 */

// Helper to obtain CSRF headers for AJAX / fetch calls
function getCsrfHeaders() {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');

    const headers = {
        'Content-Type': 'application/json'
    };

    if (tokenMeta && headerMeta) {
        const token = tokenMeta.getAttribute('content');
        const headerName = headerMeta.getAttribute('content');
        if (token && headerName) {
            headers[headerName] = token;
        }
    }

    return headers;
}

// Automatically toggle donor registration fields on register page
document.addEventListener('DOMContentLoaded', function () {
    const userTypeSelect = document.getElementById('userType');
    const donorFieldsContainer = document.getElementById('donorFields');

    if (userTypeSelect && donorFieldsContainer) {
        function updateDonorFieldsVisibility() {
            if (userTypeSelect.value === 'DONOR') {
                donorFieldsContainer.style.display = 'block';
            } else {
                donorFieldsContainer.style.display = 'none';
            }
        }

        userTypeSelect.addEventListener('change', updateDonorFieldsVisibility);
        // Run once on initial load
        updateDonorFieldsVisibility();
    }
});
