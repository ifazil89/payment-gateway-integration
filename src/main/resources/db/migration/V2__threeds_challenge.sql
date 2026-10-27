-- 3DS challenge: where the shopper goes back to afterwards, and the N-Genius link that completes the challenge.
ALTER TABLE payment ADD COLUMN return_url VARCHAR(2048);
ALTER TABLE payment ADD COLUMN threeds_challenge_response_url VARCHAR(2048);
