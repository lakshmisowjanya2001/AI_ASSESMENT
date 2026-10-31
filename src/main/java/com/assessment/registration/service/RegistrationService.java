package com.assessment.registration.service;

/**
 * Combined service boundary uniting command and query contracts for a bounded course offering.
 *
 * <p>Allows clients needing only reads to depend on {@link RegistrationQueryService},
 * clients needing only mutations to depend on {@link RegistrationCommandService}, and
 * decorators/orchestrators to compose over {@code RegistrationService} (ISP, OCP, DIP).
 */
public interface RegistrationService extends RegistrationCommandService, RegistrationQueryService {
}
