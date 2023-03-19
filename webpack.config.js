const { resolve } = require('path');
const WatchExternalFilesPlugin = require('webpack-watch-files-plugin').default;
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin');
const assets = 'src/main/resources/static/build';

module.exports = {
    mode: 'development',
    entry: {
        bundle: {
            import: './frontend/index.js',
            library: {
                name: 'prostLib',
                type: 'var',
            },
        },
        summary_page: './frontend/summary_page/index.js',
        admin: './frontend/admin/index.js',
    },
    output: {
        path: resolve(__dirname, assets),
        publicPath: '/build/',
        filename: '[name].js',
    },
    plugins: [
        new MiniCssExtractPlugin({
            filename: '[name].css',
        }),
        new WatchExternalFilesPlugin({
            files: ['src/main/resources/templates/**/*.html'],
        }),
        new CssMinimizerPlugin(),
    ],
    module: {
        rules: [
            {
                test: /\.css$/i,
                include: resolve(__dirname, 'frontend'),
                use: [
                    MiniCssExtractPlugin.loader,
                    'css-loader',
                    'postcss-loader',
                ],
                sideEffects: true,
            },
        ],
    },
};
