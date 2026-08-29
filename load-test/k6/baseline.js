import { runInterview, thresholds } from './lib/interview.js';

export const options = {
    scenarios: {
        baseline: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 20),
            duration: __ENV.DURATION || '1m',
            gracefulStop: '30s',
        },
    },
    thresholds,
};

export default runInterview;
