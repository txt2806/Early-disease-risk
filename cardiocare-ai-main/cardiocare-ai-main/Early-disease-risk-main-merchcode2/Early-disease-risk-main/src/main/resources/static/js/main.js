// Auto-hide flash messages after 3 seconds
document.addEventListener('DOMContentLoaded', function () {
    const flash = document.querySelector('.alert-success');
    if (flash) {
        setTimeout(() => {
            flash.style.transition = 'opacity 0.5s';
            flash.style.opacity = '0';
            setTimeout(() => flash.remove(), 500);
        }, 3000);
    }
});
