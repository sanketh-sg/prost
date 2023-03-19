export const pagination = {
    changePage: elem => {
        if (elem.classList.contains('disabled')) {
            return;
        }

        let pageInput = document.querySelector(
            '#pagination-form input[name="page"]'
        );

        if (
            document.querySelector('#pagination-form input[name="page"]') ==
            null
        ) {
            document
                .querySelector('#pagination-form')
                .insertAdjacentHTML(
                    'afterbegin',
                    `<input type="hidden" name="page" value="${0}">`
                );

            pageInput = document.querySelector(
                '#pagination-form input[name="page"]'
            );
        }

        const currentPage = parseInt(pageInput.value);
        const maxPage = parseInt(
            document
                .querySelector('#pagination-form')
                .getAttribute('data-pagination-max-page')
        );

        switch (elem.getAttribute('data-pagination')) {
            case '0':
                pageInput.remove();
                break;
            case '+1':
                if (currentPage + 1 > maxPage) {
                    pageInput.value = maxPage;
                } else {
                    pageInput.value = currentPage + 1;
                }
                break;
            case '-1':
                if (currentPage - 1 <= 0) {
                    pageInput.value = 0;
                } else {
                    pageInput.value = currentPage - 1;
                }
                break;
            case 'max':
                pageInput.value = maxPage;
                break;
        }
        document.querySelector('#pagination-form').submit();
    },
};
