export const transitions = {
    slideOffScreen: selector => {
        const elem = document.querySelector(selector);
        elem.animate([{ transform: `translateX(${screen.width}px)` }], {
            duration: 500,
            iterations: 1,
        });
        setTimeout(() => (elem.style.display = 'none'), 500);
    },
};