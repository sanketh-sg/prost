module.exports = {
    content: [
        './src/main/resources/templates/**/*.html',
        './frontend/**/*.css',
    ],
    theme: {
        extend: {
            colors: {
                'footer-bg': '#F2EBEB',
            },
            backgroundImage: {
                alcoholic: "url('/static/images/alcoholic.png')",
                nonalcoholic: "url('/static/images/non-alcoholic.png')",
            },
        },
    },
    variants: {
        extend: {
            visibility: ['group-hover'],
        },
    },
    plugins: [],
};
