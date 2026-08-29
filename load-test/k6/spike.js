import { runInterview, thresholds } from './lib/interview.js';

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '20s', target: 10 },
                { duration: '10s', target: 200 },
                { duration: '30s', target: 200 },
                { duration: '10s', target: 10 },
                { duration: '20s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds,
};

export default runInterview;
