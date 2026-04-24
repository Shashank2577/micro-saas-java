1. **Explore APIs and Structs**: Find out details of `ChatCompletionResponse`, `ChatCompletionRequest`, `AiDuplicateCheckResponse`, `AiPriorityResponse`, `AiRewriteResponse`, `AiTitleResponse`, `LiteLlmApi`, `JwtTenantResolver`, `GlobalExceptionHandler`, and `LocalTenantResolver`.
2. **Update pom.xml**: Add `spring-boot-starter-test` to `saas-os-core/pom.xml`.
3. **Write `AiServiceTest.java`**: Using Mockito and JUnit 5 to cover `AiService`. Handle the JSON parsing paths and error scenarios as requested.
4. **Write `JwtTenantResolverTest.java`**: Testing `tenant_id` extraction from `Jwt` tokens.
5. **Write `GlobalExceptionHandlerTest.java`**: Test `GlobalExceptionHandler` methods directly with created exceptions.
6. **Write `LocalTenantResolverTest.java`**: Test local tenant resolution deterministic UUID generation.
7. **Verify Tests**: Run `mvn test -pl saas-os-core` and make sure it succeeds.
8. **Pre-commit and Handoff**: Run pre-commit instructions and then write `IMPLEMENTATION_LOG.md`, `VERIFICATION_REPORT.md`, `HANDOFF.md` before submitting the PR.
