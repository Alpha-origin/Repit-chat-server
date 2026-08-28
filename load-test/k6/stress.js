import { runInterview, thresholds } from './lib/interview.js';

export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '1m', target: 100 },
                { duration: '1m', target: 200 },
                { duration: '1m', target: 300 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds,
};

export default runInterview;
