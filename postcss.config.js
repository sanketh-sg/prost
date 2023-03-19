module.exports = {
    plugins: {
        'tailwindcss/nesting': {},
        'postcss-preset-env': {
            features: {
                'nesting-rules': false,
            },
        },
        tailwindcss: {},
    },
};
