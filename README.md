# About this library

Camunda’s default behavior is to process any given instance of a process according to a process definition it was started for. However, sometimes an application
developer you need to introduce changes to a process that should be applied not only for newly created instances, but also for instances created before this
change. In this case the state of existing process instances should be changed in a way to not only be bind to a new process definition, but also to get process
state compatible with a new process schema. This is what we call “process migration” and what `Camunda7-migration` library was created for.

# Core features

* Process state manipulation via Camunda API
* Incremental migration strategy with SemVer support
* Support for changing process schema without rolling out a new version of a process definition — works great for testing and debugging on Dev-environments
* Appropriate migrations are applied on application start-up automatically and in a declarative manner — no need for explicit invocation of any routine
* Migrations are applied in the same transaction in which new process definitions are loaded — this prevents data corruption due to incompatibility of process state with process schema.

# Usage

A few steps should be completed in order to configure and start using `Camunda7-migration` library.

## Add dependency

Add `Camunda7-migration` dependency to pom.xml

```xml
<dependency>
  <groupId>com.atomazing.alba</groupId>
  <artifactId>camunda7-migration</artifactId>
  <version>{camunda7-migration.version}</version>
</dependency>
```

## Add autoconfiguration

Add ```@EnableCamundaMigration``` to configuration class

```java
@Configuration
@EnableCamundaMigration
public class CamundaMigrationConfiguration {
}
```

## Define process and subprocess versions

`Camunda7-migration` library uses version tag for managing process definition versions, so start with adding it to a process definition in bpmn schema:

```xml
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1uko44i" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.14.0">
  <bpmn:process id="process-id" name="ProcessName" isExecutable="true" camunda:versionTag="01.01.01">
    ...
  </bpmn:process>
</bpmn:definitions>
```

While version tag may have any format, it should be sortable. For example, Maven versions may be used. All process definitions should have a version tag. Consider the following requirements for subprocess versioning:

* Subprocess should have version tag
* If parent process changed, child process may keep old version
* If subprocess changed, parent process should be updated
* A subprocess should be called from a parent process with specific version defined (Call Activity Shape > Binding > Version Tag)

## Implement migration logic

Implement migration logic in a Java class implementing `com.atomazing.alba.camunda7.migration.api.CamundaMigration` interface and add it as a component to a Spring application context.

```java
@Component
class AcmeProcessMigration implements CamundaMigration {
    @Override
    public String key() {
        return "acme-process"; // process definition key
    }
 
    @Override
    public String source() {
        return "1.1.0";   // source version tag
    }
 
    @Override
    public String target() {
        return "1.2.2";  // target version tag
    }
 
    @Override
    public void migrate(CamundaMigrationContext context) {
        // Migration code.
    }
}
```

Where:

* method `key()` returns process definition id for which this migration should be applied,
* method `source()` returns a source version of process definition,
* method `target()` returns target version,
* method `migrate(CamundaMigrationContext context)` implements migration with using Camunda API: [Process Instance Migration](https://docs.camunda.org/manual/latest/user-guide/process-engine/process-instance-migration/), [Process Instance Modification](https://docs.camunda.org/manual/latest/user-guide/process-engine/process-instance-modification/)

### Example

```java
@Override
public void migrate(CamundaMigrationContext context) {
    // Getting source process definition id
    String sourceDefinitionId = context.getSourceDefinition().getId();

    // Getting target process definition id
    String targetDefinitionId = context.getTargetDefinition().getId();

    RuntimeService runtimeService = context.getProcessEngine().getRuntimeService();
    
    // Creating migration plan from source to target by using Camunda migration api
    runtimeService.newMigration(runtimeService
            .createMigrationPlan(sourceDefinitionId, targetDefinitionId)
            .mapEqualActivities()
            .build())
        .processInstanceIds(context.getProcessIds()) // context includes process instance ids on source process definition version 
        .skipCustomListeners()
        .skipIoMappings()
        .execute();
}
```

### Complex example

```java
@Override
public void migrate(CamundaMigrationContext context) {
    // Getting source process definition id
    String sourceDefinitionId = context.getSourceDefinition().getId();

    // Getting target process definition id
    String targetDefinitionId = context.getTargetDefinition().getId();
 
    RuntimeService runtimeService = context.getProcessEngine().getRuntimeService();
    TaskService taskService = context.getProcessEngine().getTaskService();
 
    // Creating migration plan from source to target by using Camunda migration api
    runtimeService.newMigration(runtimeService
        .createMigrationPlan(sourceDefinitionId, targetDefinitionId)
            .mapEqualActivities()
                                    // All instnces on old shape 'fill-data' map to shape 'fill-data-migration' in new version
            .mapActivities("fill-data", "fill-data-migration")
            .build())
        .processInstanceIds(context.getProcessIds())
        .skipCustomListeners()
        .skipIoMappings()
        .execute();
  
    // Closing task fill-data-migration 
    taskService.createTaskQuery()
        .processInstanceIdIn(context.getProcessIds().toArray(String[]::new))
        .list().stream()
        .filter(task -> task.getTaskDefinitionKey().equals("task-migration"))
        .map(Task::getId)
        .forEach(taskService::complete);    
}
```

# Migration strategy

`Camunda7-migration` uses incremental strategy for migration.
For example, we have process with versions 01.01.01, 01.01.02 and 01.02.01 and migration scripts from 01.01.01 to 01.01.02 and from 01.01.02 to 01.02.01.

On service startup `Camunda7-migration` library will find all instances with version 01.01.01 and apply migration to 01.01.02. Migrated process instances will get version 01.01.02. Then library will find all instances with version 01.01.02 and apply migration to 01.02.01. Migrated process instances will finally get version 01.02.01.

# File naming

It's recommended to use version in bpmn file name. Also, it is best practice combining process definitions with same version version in one directory.
Example:

* src/main/resources/bpmn
  * 0101
    * acme-process-01.01.01.bpmn
    * subprocess-01.01.01.bpmn
  * 0102
    * acme-process-01.02.01.bpmn