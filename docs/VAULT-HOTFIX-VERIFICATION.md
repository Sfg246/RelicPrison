# Vault startup hotfix verification

## Failure reproduced

The Stage 14 JAR was compiled in the offline verification environment against a development stub that incorrectly declared Bukkit's `RegisteredServiceProvider` as an interface. That produced an `invokeinterface` instruction for `getProvider()`.

Paper 1.21.10 provides `RegisteredServiceProvider` as a class, so the JVM rejected the old bytecode with:

```text
IncompatibleClassChangeError: Found class org.bukkit.plugin.RegisteredServiceProvider, but interface was expected
```

## Fix

`VaultEconomyAdapter` no longer invokes `RegisteredServiceProvider#getProvider()` through a statically linked type. The registration is handled as `Object`, and the public `getProvider` method is resolved reflectively at the narrow Vault boundary.

The rest of the economy adapter remains unchanged:

- Vault remains the source for balances, withdrawals, and deposits.
- Items are removed only after a successful deposit.
- Rank and prestige charges continue to use the same economy adapter.
- No database or configuration format changed.

## Verification

- Java 21 production compile: passed
- Unit/domain tests: 20 passed
- Stage 11-14 assertions: 17 passed
- Classfile ABI guard: no `InterfaceMethod RegisteredServiceProvider.getProvider` reference
- YAML resources: parsed successfully
- POM and Checkstyle XML: parsed successfully

A full Paper restart remains required for installation. Runtime plugin replacement through DeepReload is not a valid classloader compatibility test for this update.
