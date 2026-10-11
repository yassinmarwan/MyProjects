
MSA M2 Test Scenarios


#	Title	Tags	Endpoint(s)	What it tests	Steps	Pass criteria
1	TC01 — POST registerPath() with a fresh email returns 2xx and a JWT token	public, features_m2	POST registerPath()	S1-F10 — Register a new user (happy path). Implementation: RegisterHappyPathTests at PublicTests.java:50.	httpPost	• TC01 register
• TC01: register response must include 'token' field per spec; body=
• TC01: 'token' must be a non-blank string; got
• TC01: register response must include positive 'expiresIn' per spec; body=
2	TC02 — POST loginPath() after a successful register returns 2xx with a 3-segment JWT	public, features_m2	POST registerPath() · POST loginPath()	S1-F11 — Login with valid credentials (happy path). Implementation: LoginHappyPathTests at PublicTests.java:84.	httpPost	• TC02 setup register
• TC02 login
• TC02: login response must include 'token' field; body=
• TC02: 'token' must be a non-blank string
• TC02: 'token' must be a 3-segment JWT (a.b.c); got '
3	TC03 — GET crudReadPath("User") with own JWT returns 2xx and a JSON object	public, updated_crud	POST registerPath() · POST loginPath() · GET crudReadPathFor("User", uid)	Read own user profile with valid JWT (happy path). Implementation: ReadOwnProfileHappyPathTests at PublicTests.java:121.	httpPost → httpGet	• TC03 setup register
• TC03 setup login
• TC03 read own profile
• TC03: response body must be a JSON object; got
4	TC04 — POST registerPath() with an already-registered email returns a 4xx	public, features_m2	POST registerPath()	S1-F10 — Register with duplicate email returns 4xx (negative path). Implementation: RegisterDuplicateEmailTests at PublicTests.java:163.	httpPost	• TC04 first register (precondition)
• TC04: duplicate-email register must return a 4xx client error;
• 401 indicates the controller is misclassifying the error. body=
• 403 indicates the controller is misclassifying the error. body=
5	TC05 — POST loginPath() with the wrong password returns strictly 401	public, features_m2	POST registerPath() · POST loginPath()	S1-F11 — Login with wrong password returns 401 (negative path). Implementation: LoginWrongPasswordTests at PublicTests.java:207.	httpPost	• TC05 setup register (precondition)
• A 2xx here means the password check was skipped — critical security bug.
• translated to 401. Got
• (OWASP user-enumeration anti-pattern). body=
• obtaining permissions, not exercising them — 403 is structurally wrong.
• TC05: login with wrong password must return strictly 401 Unauthorized; got
6	TC06 — GET a non-User CRUD list endpoint with a valid admin Bearer JWT returns 2xx (auth filter accepts the token)	public, authentication	GET path	Authentication happy path: valid admin JWT accepted on a non-User CRUD. Implementation: AuthValidTokenAcceptedTests at PublicTests.java:269.	adminToken → httpGet	• TC06 auth happy path (admin) on
7	TC07 — GET a non-User CRUD list endpoint with NO Authorization header returns strictly 401	public, authentication	GET path	Missing Authorization header on a non-User CRUD returns 401. Implementation: AuthMissingHeaderTests at PublicTests.java:322.	httpGet	• A 2xx here means the endpoint is wide-open — critical security bug.
• means the filter chain crashed instead of cleanly rejecting.
• runs. body=
• code is 401 Unauthorized. body=
• Unauthorized; got
8	TC08 — GET protected endpoint with a tampered-signature JWT returns strictly 401 (signature is verified, not just decoded)	public, authentication	GET path	Tampered JWT signature is rejected with 401 (negative path). Implementation: AuthTamperedSignatureTests at PublicTests.java:375.	adminToken → httpGet	• forge tokens by editing the payload. Critical security bug. body=
• rejecting. body=
• the filter, before any controller logic runs. body=
• lacking permission" (403). body=
• TC08: tampered-signature JWT must return strictly 401 Unauthorized; got
9	TC09 — POST loginPath() with an email that has never been registered returns strictly 401	public, features_m2	POST loginPath()	S1-F11 — Login with non-existent email returns 401 (negative path). Implementation: LoginUnknownEmailTests at PublicTests.java:437.	httpPost	• user. body=
• ). Server must handle the missing-user case cleanly. body=
• ). 403 is a permission error; this is an unauthenticated condition. body=
• (spec §10 S1-F11 — 401 for both 'user not found' and 'wrong password');
10	TC10 — GET protected endpoint with Authorization: Bearer  (empty token) returns strictly 401	public, authentication	GET path	Empty Bearer token returns 401 (negative path). Implementation: AuthEmptyBearerTests at PublicTests.java:473.	httpGet	• ). A 2xx here means the auth filter accepted an empty/missing token.
• handle empty tokens cleanly, not throw NPE. body=
• TC10: empty Bearer token must return strictly 401 Unauthorized; got
11	TC11 — GET protected endpoint with Authorization: Basic ... (non-Bearer scheme) returns strictly 401	public, authentication	GET path	Non-Bearer scheme (Basic) returns 401 (negative path). Implementation: AuthBasicSchemeTests at PublicTests.java:500.	httpGet	• ). The filter must reject any non-Bearer scheme.
• TC11: Basic-scheme auth must NOT 5xx (status
• TC11: Basic-scheme auth must return strictly 401 Unauthorized; got
12	TC12 — GET protected endpoint with Authorization: Bearer not_a_valid_jwt returns strictly 401	public, authentication	GET path	Garbage non-JWT token returns 401 (negative path). Implementation: AuthGarbageTokenTests at PublicTests.java:526.	httpGet	• ). A 2xx here means the filter didn't validate the JWT structure.
• handle malformed tokens gracefully. body=
• TC12: garbage non-JWT token must return strictly 401 Unauthorized; got
13	TC13 — GET protected endpoint with a payload-tampered JWT (role forged to ADMIN) is NOT accepted	public, authentication	POST registerPath() · POST loginPath() · GET path	Forged role-claim token (payload modified post-signing) rejected. Implementation: AuthForgedRoleClaimTests at PublicTests.java:553.	httpPost → httpGet	• TC13 setup register (precondition)
• TC13 setup login (precondition)
• editing their payload. Critical privilege-escalation bug. body=
• must reject cleanly. body=
14	TC14 — POST registerPath() with a body missing the email field returns a 4xx	public, features_m2	POST registerPath()	Register with missing required field returns 4xx (negative path). Implementation: RegisterMissingFieldTests at PublicTests.java:640.	httpPost	• TC14: register with missing required field (email) must return a 4xx; got
• cannot be valid). body=
• validate input cleanly, not crash. body=
15	TC15 — POST registerPath() with role:ADMIN in body must NOT result in an ADMIN account (privilege-escalation)	public, features_m2	POST registerPath()	Register with role=ADMIN in body must NOT yield an ADMIN account. Implementation: RegisterRoleEscalationTests at PublicTests.java:668.	httpPost	• TC15: register with role=ADMIN body must NOT 5xx (got
• — register isn't actually persisting.
• controller is mapping the body's role field into the entity.
16	TC16 — POST loginPath() with password:"" (empty) returns NOT 2xx	public, features_m2	POST registerPath() · POST loginPath()	Login with empty password returns 4xx (negative path). Implementation: LoginEmptyPasswordTests at PublicTests.java:702.	httpPost	• TC16 setup register
• input. body=
• controller must validate input cleanly, not NPE on empty string.
• failure); got
17	TC17 — Customer A's GET on User B's CRUD path must NOT be 2xx (cross-user IDOR)	public, authorization	POST registerPath() · POST loginPath() · GET crudReadPathFor("User", bid)	Cross-user IDOR: User A cannot READ User B's profile. Implementation: IdorReadOtherUserTests at PublicTests.java:737.	httpPost → httpGet	• TC17 setup register A
• TC17 setup register B
• TC17 setup login A
• authenticated user can read any other user's profile. body=
• must reject cleanly, not crash. body=
• privacy-by-obscurity); got
18	TC18 — Customer A's PUT on User B's CRUD path must NOT be 2xx, AND B's data must NOT change in DB	public, authorization	POST registerPath() · POST loginPath() · PUT crudReadPathFor("User", bid)	Cross-user IDOR: User A cannot UPDATE User B's profile. Implementation: IdorUpdateOtherUserTests at PublicTests.java:783.	httpPost → httpPut → jdbc.query	• TC18 setup register A
• TC18 setup register B
• TC18 setup login A
• ). A 2xx here means cross-user IDOR write is unprotected.
• TC18: cross-user update must NOT 5xx (status
• TC18: cross-user update must return 403 or 404; got
• controller is committing the change before doing the auth check.
19	TC19 — Customer A's DELETE on User B's CRUD path must NOT be 2xx, AND B must STILL exist in DB	public, authorization	POST registerPath() · POST loginPath() · DELETE crudReadPathFor("User", bid)	Cross-user IDOR: User A cannot DELETE User B. Implementation: IdorDeleteOtherUserTests at PublicTests.java:845.	httpPost → httpDelete → jdbc.query	• TC19 setup register A
• TC19 setup register B
• TC19 setup login A
• ). A 2xx here means cross-user delete is unprotected. body=
• TC19: cross-user delete must NOT 5xx (status
• TC19: cross-user delete must return 403 or 404; got
• delete before doing the auth check.
20	TC20 — Customer's PUT on their own User CRUD path returns 2xx, AND DB reflects the new name	public, authorization	POST registerPath() · POST loginPath() · PUT crudReadPathFor("User", uid)	Owner happy path: User A can UPDATE their own profile. Implementation: OwnerUpdateOwnProfileTests at PublicTests.java:899.	httpPost → httpPut → jdbc.query	• TC20 setup register
• TC20 setup login
• TC20 owner update own profile
• actually persisting the update.
21	TC21 — Admin's GET on a customer's User CRUD path returns 2xx (admin role bypasses ownership)	public, authorization	POST registerPath() · GET crudReadPathFor("User", customerId)	Admin override: admin can READ any user. Implementation: AdminReadAnyUserTests at PublicTests.java:946.	httpPost → adminToken → httpGet	• TC21 setup register customer
• TC21 admin read customer
• TC21: admin GET response body must be a JSON object; got
22	TC22 — Admin's PUT on a customer's User CRUD path returns 2xx, AND DB reflects the new name	public, authorization	POST registerPath() · PUT crudReadPathFor("User", customerId)	Admin override: admin can UPDATE any user. Implementation: AdminUpdateAnyUserTests at PublicTests.java:972.	httpPost → adminToken → httpPut → jdbc.query	• TC22 setup register customer
• TC22 admin update customer
• ') — admin update did not
23	TC23 — Admin's DELETE on a customer's User CRUD path returns 2xx, AND the row is HARD-deleted (strict)	public, authorization	POST registerPath() · DELETE crudReadPathFor("User", customerId) · GET crudReadPathFor("User", customerId)	Admin override: admin can DELETE any user (strict hard-delete). Implementation: AdminDeleteAnyUserTests at PublicTests.java:1012.	httpPost → adminToken → httpDelete → jdbc.query → httpGet	• TC23 setup register customer
• TC23 admin delete customer
• not soft-delete it (use the deactivate endpoint for status changes).
• already verified gone from DB above, but GET still finds it. body=
• TC23: GET after a successful DELETE must return 404 Not Found; got
24	TC24 — GET /api/users/{ownId}/activity with own token returns 2xx and a paginated envelope	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	Owner GET own activity returns 2xx with paginated envelope. Implementation: ActivityOwnerHappyPathTests at PublicTests.java:1066.	httpPost → httpGet	• TC24 setup register
• TC24 setup login
• TC24 owner activity
• TC24: response must include content field; body=
• TC24: content must be an array; got
• TC24: response must include page field; body=
• TC24: response must include size field; body=
• TC24: response must include totalElements field; body=
25	TC25 — GET /api/users/<Long.MAX_VALUE>/activity with admin token returns strictly 404	public, features_m2	GET activityPath	Non-existent user ID returns 404 (admin token). Implementation: ActivityNonExistentIdTests at PublicTests.java:1109.	adminToken → httpGet	• TC25: activity for a non-existent user ID must NOT be 2xx (status
• missing-user gracefully. body=
• strictly 404; got
26	TC26 — GET /api/users/-1/activity with admin token returns a 4xx (graceful)	public, features_m2	GET activityPath	Negative user ID returns 4xx (admin token). Implementation: ActivityNegativeIdTests at PublicTests.java:1137.	adminToken → httpGet	• validate / reject gracefully, not crash. status=
• match any real user. status=
• 404 not-found); got
27	TC27 — GET /api/users/abc/activity with admin token returns a 4xx (path-var binding fails)	public, features_m2	GET activityPath	String user ID returns 4xx (admin token). Implementation: ActivityStringIdTests at PublicTests.java:1165.	adminToken → httpGet	• TypeMismatchException. status=
• a valid Long. status=
• Bad Request); got
28	TC28 — GET /api/users/{ownId}/activity?size=0 must NOT 5xx (spec silent on size=0)	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	size=0 returns gracefully (NOT 5xx). Implementation: ActivitySizeZeroTests at PublicTests.java:1193.	httpPost → httpGet	• TC28 setup register
• TC28 setup login
• gracefully, not let PageRequest.of throw IllegalArgumentException.
29	TC29 — GET /api/users/{ownId}/activity?size=-1 returns a 4xx	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	size=-1 returns 4xx. Implementation: ActivityNegativeSizeTests at PublicTests.java:1229.	httpPost → httpGet	• TC29 setup register
• TC29 setup login
• TC29: size=-1 must NOT 5xx — controller must validate gracefully. status=
• TC29: size=-1 must NOT be 2xx — negative page size is semantically invalid.
• TC29: size=-1 must return a 4xx; got
30	TC30 — GET /api/users/{ownId}/activity?size=abc returns a 4xx (Integer binding fails)	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	size=string returns 4xx (binding fails). Implementation: ActivityStringSizeTests at PublicTests.java:1269.	httpPost → httpGet	• TC30 setup register
• TC30 setup login
• reject the string cleanly. status=
• TC30: size=abc must NOT be 2xx — non-numeric size cannot be valid. status=
• TC30: size=abc must return a 4xx (typically 400 Bad Request); got
31	TC31 — Customer A's GET on User B's activity returns strictly 403 (per S1-F12 spec)	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	Cross-user activity (regular user) returns strictly 403. Implementation: ActivityCrossUserRegularTests at PublicTests.java:1310.	httpPost → httpGet	• TC31 setup register A
• TC31 setup register B
• TC31 setup login A
• other users' activity feeds. status=
• TC31: cross-user activity GET must NOT 5xx. status=
• (ownership violation, NOT 404 — A's token is valid and B exists); got
32	TC32 — Admin's GET on a customer's activity returns 2xx (admin bypasses ownership)	public, features_m2	POST registerPath() · GET activityPath	Cross-user activity (admin) returns 2xx. Implementation: ActivityCrossUserAdminTests at PublicTests.java:1356.	httpPost → adminToken → httpGet	• TC32 setup register customer
• TC32 admin activity
• TC32: admin response must include content array; body=
33	TC33 — GET /api/users/{ownId}/activity?page=-1 returns a 4xx	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	page=-1 returns 4xx. Implementation: ActivityNegativePageTests at PublicTests.java:1384.	httpPost → httpGet	• TC33 setup register
• TC33 setup login
• page >= 0; controller must validate gracefully. status=
• TC33: page=-1 must NOT be 2xx — negative page is semantically invalid.
• TC33: page=-1 must return a 4xx; got
34	TC34 — GET /api/users/{ownId}/activity?page=abc returns a 4xx (Integer binding fails)	public, features_m2	POST registerPath() · POST loginPath() · GET activityPath	page=string returns 4xx (binding fails). Implementation: ActivityStringPageTests at PublicTests.java:1425.	httpPost → httpGet	• TC34 setup register
• TC34 setup login
• reject the string cleanly. status=
• TC34: page=abc must NOT be 2xx — non-numeric page cannot be valid. status=
• TC34: page=abc must return a 4xx (typically 400 Bad Request); got