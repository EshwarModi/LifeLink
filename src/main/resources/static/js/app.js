/**
 * LifeLink Client Script
 *
 * Provides:
 * - CSRF token extraction for secure fetch() calls
 * - Mobile navigation drawer toggle (ARIA + Escape key handling)
 * - Auto-dismissing flash toast notifications (5s timer with hover-pause)
 * - Accessible confirmation modals for destructive actions (WCAG alertdialog)
 * - Dynamic form visibility for role-specific registration fields
 */

(function () {
    'use strict';

    /**
     * Extracts CSRF tokens from layout meta tags for fetch/XHR calls.
     * @returns {Record<string, string>}
     */
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

    /**
     * Dismisses a single alert/toast element with a smooth fade-out animation.
     * @param {HTMLElement} toastEl
     */
    function dismissToast(toastEl) {
        if (!toastEl || toastEl.classList.contains('is-dismissing')) {
            return;
        }
        toastEl.classList.add('is-dismissing');
        window.setTimeout(function () {
            if (toastEl.parentNode) {
                toastEl.parentNode.removeChild(toastEl);
            }
        }, 180);
    }

    /**
     * Accessible confirmation modal dialog for destructive actions (WCAG 2.1 AA compliant).
     * @param {Object} options
     * @param {string} [options.title='Confirm Action']
     * @param {string} [options.message='Are you sure you want to proceed?']
     * @param {string} [options.confirmLabel='Confirm']
     * @param {string} [options.cancelLabel='Cancel']
     * @param {Function} [options.onConfirm]
     */
    function showConfirmDialog(options) {
        const title = (options && options.title) || 'Confirm Action';
        const message = (options && options.message) || 'Are you sure you want to proceed? This action may not be reversible.';
        const confirmLabel = (options && options.confirmLabel) || 'Confirm';
        const cancelLabel = (options && options.cancelLabel) || 'Cancel';
        const onConfirm = options && options.onConfirm;

        const backdrop = document.createElement('div');
        backdrop.className = 'confirm-backdrop';
        backdrop.setAttribute('role', 'presentation');

        const dialog = document.createElement('div');
        dialog.className = 'confirm-dialog';
        dialog.setAttribute('role', 'alertdialog');
        dialog.setAttribute('aria-modal', 'true');
        dialog.setAttribute('aria-labelledby', 'confirmDialogTitle');
        dialog.setAttribute('aria-describedby', 'confirmDialogDesc');

        const heading = document.createElement('h3');
        heading.id = 'confirmDialogTitle';
        heading.textContent = title;

        const desc = document.createElement('p');
        desc.id = 'confirmDialogDesc';
        desc.textContent = message;

        const actions = document.createElement('div');
        actions.className = 'confirm-actions';

        const cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.className = 'btn btn-secondary btn-sm';
        cancelBtn.textContent = cancelLabel;

        const confirmBtn = document.createElement('button');
        confirmBtn.type = 'button';
        confirmBtn.className = 'btn btn-danger btn-sm';
        confirmBtn.textContent = confirmLabel;

        actions.appendChild(cancelBtn);
        actions.appendChild(confirmBtn);
        dialog.appendChild(heading);
        dialog.appendChild(desc);
        dialog.appendChild(actions);
        backdrop.appendChild(dialog);
        document.body.appendChild(backdrop);

        const previousFocus = document.activeElement;
        cancelBtn.focus();

        function closeDialog() {
            document.removeEventListener('keydown', handleKeydown);
            if (backdrop.parentNode) {
                backdrop.parentNode.removeChild(backdrop);
            }
            if (previousFocus && typeof previousFocus.focus === 'function') {
                previousFocus.focus();
            }
        }

        function handleKeydown(e) {
            if (e.key === 'Escape') {
                e.preventDefault();
                closeDialog();
            } else if (e.key === 'Tab') {
                // Trap focus inside modal
                const focusable = [cancelBtn, confirmBtn];
                const first = focusable[0];
                const last = focusable[focusable.length - 1];

                if (e.shiftKey && document.activeElement === first) {
                    e.preventDefault();
                    last.focus();
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault();
                    first.focus();
                }
            }
        }

        document.addEventListener('keydown', handleKeydown);
        cancelBtn.addEventListener('click', closeDialog);
        backdrop.addEventListener('click', function (e) {
            if (e.target === backdrop) {
                closeDialog();
            }
        });

        confirmBtn.addEventListener('click', function () {
            closeDialog();
            if (typeof onConfirm === 'function') {
                onConfirm();
            }
        });
    }

    // Expose global helpers
    window.LifeLink = {
        getCsrfHeaders: getCsrfHeaders,
        dismissToast: dismissToast,
        confirmAction: showConfirmDialog
    };

    // DOM Ready initialization
    document.addEventListener('DOMContentLoaded', function () {
        // 1. Mobile Drawer Navigation Toggle
        const navToggle = document.getElementById('navToggle');
        const primaryNav = document.getElementById('primaryNav');

        if (navToggle && primaryNav) {
            function closeMobileNav() {
                primaryNav.classList.remove('is-open');
                navToggle.setAttribute('aria-expanded', 'false');
                document.body.classList.remove('nav-open');
            }

            navToggle.addEventListener('click', function (e) {
                e.stopPropagation();
                const isExpanded = navToggle.getAttribute('aria-expanded') === 'true';
                const nextState = !isExpanded;
                navToggle.setAttribute('aria-expanded', String(nextState));
                primaryNav.classList.toggle('is-open', nextState);
                document.body.classList.toggle('nav-open', nextState);
            });

            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' && navToggle.getAttribute('aria-expanded') === 'true') {
                    closeMobileNav();
                    navToggle.focus();
                }
            });

            document.addEventListener('click', function (e) {
                if (navToggle.getAttribute('aria-expanded') === 'true' &&
                    !primaryNav.contains(e.target) &&
                    !navToggle.contains(e.target)) {
                    closeMobileNav();
                }
            });
        }

        // 2. Flash Alerts & Auto-Dismissal (5000ms duration with hover-pause)
        const toasts = document.querySelectorAll('[data-toast]');
        toasts.forEach(function (toastEl) {
            let timerId = window.setTimeout(function () {
                dismissToast(toastEl);
            }, 5000);

            toastEl.addEventListener('mouseenter', function () {
                if (timerId) {
                    window.clearTimeout(timerId);
                    timerId = null;
                }
            });

            toastEl.addEventListener('mouseleave', function () {
                if (!timerId) {
                    timerId = window.setTimeout(function () {
                        dismissToast(toastEl);
                    }, 2500);
                }
            });

            const dismissBtn = toastEl.querySelector('[data-dismiss-alert]');
            if (dismissBtn) {
                dismissBtn.addEventListener('click', function () {
                    if (timerId) {
                        window.clearTimeout(timerId);
                    }
                    dismissToast(toastEl);
                });
            }
        });

        // 3. Declarative Confirm Dialog Helper for Destructive Forms / Buttons
        document.addEventListener('submit', function (event) {
            const form = event.target;
            if (!(form instanceof HTMLFormElement)) {
                return;
            }
            const confirmMessage = form.getAttribute('data-confirm');
            if (confirmMessage && form.getAttribute('data-confirmed') !== 'true') {
                event.preventDefault();
                const confirmTitle = form.getAttribute('data-confirm-title') || 'Confirm Action';
                const confirmLabel = form.getAttribute('data-confirm-label') || 'Confirm';
                showConfirmDialog({
                    title: confirmTitle,
                    message: confirmMessage,
                    confirmLabel: confirmLabel,
                    onConfirm: function () {
                        form.setAttribute('data-confirmed', 'true');
                        form.submit();
                    }
                });
            }
        });

        document.addEventListener('click', function (event) {
            const trigger = event.target.closest('[data-confirm]');
            if (!trigger) {
                return;
            }
            if (trigger instanceof HTMLFormElement || trigger.closest('form[data-confirm]')) {
                return;
            }
            const confirmMessage = trigger.getAttribute('data-confirm');
            if (confirmMessage && trigger.getAttribute('data-confirmed') !== 'true') {
                event.preventDefault();
                const confirmTitle = trigger.getAttribute('data-confirm-title') || 'Confirm Action';
                const confirmLabel = trigger.getAttribute('data-confirm-label') || 'Confirm';
                showConfirmDialog({
                    title: confirmTitle,
                    message: confirmMessage,
                    confirmLabel: confirmLabel,
                    onConfirm: function () {
                        trigger.setAttribute('data-confirmed', 'true');
                        if (trigger.tagName === 'A' && trigger.href) {
                            window.location.href = trigger.href;
                        } else if (trigger.form) {
                            trigger.form.submit();
                        } else {
                            trigger.click();
                        }
                    }
                });
            }
        });

        // 4. Accessible Password Show/Hide Toggle Helper
        document.querySelectorAll('[data-password-toggle]').forEach(function (toggleBtn) {
            toggleBtn.addEventListener('click', function () {
                const targetId = toggleBtn.getAttribute('data-password-target') || 'password';
                const input = document.getElementById(targetId);
                if (!input) {
                    return;
                }
                const isPassword = input.getAttribute('type') === 'password';
                input.setAttribute('type', isPassword ? 'text' : 'password');
                toggleBtn.setAttribute('aria-pressed', String(isPassword));
                toggleBtn.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');

                const iconUse = toggleBtn.querySelector('use');
                if (iconUse) {
                    iconUse.setAttribute('href', isPassword ? '#icon-eye-off' : '#icon-eye');
                }
            });
        });

        // 5. Live Password Policy Checklist
        const passwordInput = document.getElementById('password');
        const checklist = document.getElementById('passwordChecklist');
        if (passwordInput && checklist) {
            const rules = {
                length: checklist.querySelector('[data-rule="length"]'),
                upper: checklist.querySelector('[data-rule="upper"]'),
                lower: checklist.querySelector('[data-rule="lower"]'),
                digit: checklist.querySelector('[data-rule="digit"]')
            };

            function updateRuleItem(item, valid) {
                if (!item) {
                    return;
                }
                item.classList.toggle('is-valid', valid);
                const icon = item.querySelector('.checklist-icon');
                if (icon) {
                    icon.innerHTML = valid ? '✓' : '';
                }
            }

            passwordInput.addEventListener('input', function () {
                const val = passwordInput.value || '';
                updateRuleItem(rules.length, val.length >= 8);
                updateRuleItem(rules.upper, /[A-Z]/.test(val));
                updateRuleItem(rules.lower, /[a-z]/.test(val));
                updateRuleItem(rules.digit, /[0-9]/.test(val));
            });
        }

        // 6. Interactive Role Selector (Two large selectable cards driving #userType)
        const userTypeSelect = document.getElementById('userType');
        const roleCards = document.querySelectorAll('[data-role-option]');
        const donorFieldsContainer = document.getElementById('donorFields');

        if (roleCards.length && userTypeSelect) {
            function applySelectedRole(role) {
                userTypeSelect.value = role;
                roleCards.forEach(function (card) {
                    const cardRole = card.getAttribute('data-role-option');
                    const isMatch = cardRole === role;
                    card.classList.toggle('is-selected', isMatch);
                    card.setAttribute('aria-checked', String(isMatch));
                });

                if (donorFieldsContainer) {
                    if (role === 'DONOR') {
                        donorFieldsContainer.classList.remove('is-collapsed');
                        donorFieldsContainer.removeAttribute('aria-hidden');
                        donorFieldsContainer.style.display = 'block';
                    } else {
                        donorFieldsContainer.classList.add('is-collapsed');
                        donorFieldsContainer.setAttribute('aria-hidden', 'true');
                        window.setTimeout(function () {
                            if (userTypeSelect.value !== 'DONOR') {
                                donorFieldsContainer.style.display = 'none';
                            }
                        }, 260);
                    }
                }
            }

            roleCards.forEach(function (card) {
                card.addEventListener('click', function () {
                    const role = card.getAttribute('data-role-option');
                    applySelectedRole(role);
                });

                card.addEventListener('keydown', function (e) {
                    if (e.key === ' ' || e.key === 'Enter') {
                        e.preventDefault();
                        const role = card.getAttribute('data-role-option');
                        applySelectedRole(role);
                    }
                });
            });

            // Initial sync from server-provided value or default to DONOR
            const initialRole = userTypeSelect.value || 'DONOR';
            applySelectedRole(initialRole);
        } else if (userTypeSelect && donorFieldsContainer) {
            // Fallback for standard select without cards
            function updateDonorFieldsVisibility() {
                const isDonor = userTypeSelect.value === 'DONOR';
                donorFieldsContainer.style.display = isDonor ? 'block' : 'none';
            }
            userTypeSelect.addEventListener('change', updateDonorFieldsVisibility);
            updateDonorFieldsVisibility();
        }

        // 7. Form Submission Guard (Prevent double post)
        document.querySelectorAll('form[data-prevent-double-submit]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                if (form.getAttribute('data-submitting') === 'true') {
                    e.preventDefault();
                    return;
                }
                const submitBtn = form.querySelector('button[type="submit"]');
                if (submitBtn) {
                    form.setAttribute('data-submitting', 'true');
                    submitBtn.disabled = true;
                    const loadingText = submitBtn.getAttribute('data-loading-text') || 'Processing...';
                    submitBtn.textContent = loadingText;
                }
            });
        });

        // 8. Tomorrow Min-Date Enforcement for Date Pickers
        const requiredByInputs = document.querySelectorAll('#requiredBy, input[data-min-tomorrow]');
        if (requiredByInputs.length > 0) {
            const tomorrow = new Date();
            tomorrow.setDate(tomorrow.getDate() + 1);
            const yyyy = tomorrow.getFullYear();
            const mm = String(tomorrow.getMonth() + 1).padStart(2, '0');
            const dd = String(tomorrow.getDate()).padStart(2, '0');
            const minDateStr = yyyy + '-' + mm + '-' + dd;

            requiredByInputs.forEach(function (input) {
                input.setAttribute('min', minDateStr);
            });
        }

        // 9. Live Character Counter for Textareas (e.g. #reason)
        const charCountTargets = document.querySelectorAll('textarea[maxlength], textarea[data-char-counter]');
        charCountTargets.forEach(function (textarea) {
            const counterId = textarea.getAttribute('data-char-counter') || (textarea.id ? textarea.id + 'Counter' : null);
            const counterEl = counterId ? document.getElementById(counterId) : null;
            if (counterEl) {
                const max = parseInt(textarea.getAttribute('maxlength') || '1000', 10);
                function updateCounter() {
                    const currentLen = (textarea.value || '').length;
                    counterEl.textContent = currentLen + ' / ' + max + ' characters';
                    if (currentLen >= max) {
                        counterEl.style.color = 'var(--color-danger)';
                    } else if (currentLen >= max * 0.9) {
                        counterEl.style.color = 'var(--color-urgency-high)';
                    } else {
                        counterEl.style.color = 'var(--color-text-muted)';
                    }
                }
                textarea.addEventListener('input', updateCounter);
                updateCounter();
            }
        });

        // 10. Relative Date Hints ("in 2 days", "Tomorrow", etc.)
        const relativeDateEls = document.querySelectorAll('[data-relative-date]');
        relativeDateEls.forEach(function (el) {
            const dateStr = el.getAttribute('data-relative-date');
            if (!dateStr) {
                return;
            }
            const targetDate = new Date(dateStr + 'T00:00:00');
            const today = new Date();
            today.setHours(0, 0, 0, 0);

            if (isNaN(targetDate.getTime())) {
                return;
            }

            const diffTime = targetDate.getTime() - today.getTime();
            const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));

            let hintText = '';
            let urgencyClass = '';

            if (diffDays < 0) {
                const pastDays = Math.abs(diffDays);
                hintText = pastDays === 1 ? 'Expired yesterday' : 'Expired ' + pastDays + 'd ago';
                urgencyClass = 'is-urgent';
            } else if (diffDays === 0) {
                hintText = 'Due today';
                urgencyClass = 'is-urgent';
            } else if (diffDays === 1) {
                hintText = 'Tomorrow';
                urgencyClass = 'is-urgent';
            } else if (diffDays <= 3) {
                hintText = 'in ' + diffDays + ' days';
                urgencyClass = 'is-soon';
            } else {
                hintText = 'in ' + diffDays + ' days';
            }

            el.textContent = hintText;
            if (urgencyClass) {
                el.classList.add(urgencyClass);
            }
        });
    });
})();
