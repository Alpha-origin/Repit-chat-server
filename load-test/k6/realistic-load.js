import { runInterview, thresholds } from './lib/interview.js';

const ANSWER_DELAY_MS = Number(__ENV.ANSWER_DELAY_MS || 5000);

export const options = {
    scenarios: {
        realistic_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '1m', target: 50 },
                { duration: '1m', target: 100 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds,
};

export default function realisticInterview() {
    runInterview(ANSWER_DELAY_MS);
}
