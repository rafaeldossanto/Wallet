/**
 * Shared kernel used by every module: money, errors, pagination, JSON and time.
 *
 * <p>Declared as an open module so the other modules can use its subpackages
 * without each one having to be published as a named interface.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.wallet.core.shared;

import org.springframework.modulith.ApplicationModule;
