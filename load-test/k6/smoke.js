import { runInterview, thresholds } from './lib/interview.js';

export const options = {
    scenarios: {
        smoke: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '1m',
        },
    },
    thresholds,
};

export default runInterview;
