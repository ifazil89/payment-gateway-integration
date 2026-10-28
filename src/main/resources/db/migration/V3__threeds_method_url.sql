-- 3DS challenge page: the backend posts the CReq to N-Genius' threeDSMethodURL and returns the HTML to the client.
ALTER TABLE payment ADD COLUMN threeds_method_url VARCHAR(2048);
