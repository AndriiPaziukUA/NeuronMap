# Legacy cleanup audit — 2026-10-09

Аудит поточного `master` показав, що Java-класи `ProjectCatalogStore` і `LegacyDatabaseMigrator` уже відсутні — у repository tree та code search згадок немає.

`LegacyProjectStorageMigrator` **не** є obsolete: він потрібен для одноразового перенесення старого storage layout.

Залишилися tracked/runtime artifacts у корені репозиторію:

```text
neuronmap.db
neuronmap-projects\
neuronmap-projects.properties
neuronmap-global.properties
neuronmap-window.properties
.idea/dataSources.xml
```

`dataSources.xml` містить JDBC URL до `D:\Projects\Java\NeuronMap\neuronmap.db`, тобто прив'язаний до вже obsolete root database.

Після storage refactor production code використовує:

```text
%USERPROFILE%\Documents\NeuronMap
```

Тому старі root database/project registry файли не повинні залишатися частиною репозиторію.

Для cleanup використовується:

```text
scripts/Remove-NeuronMapLegacyArtifacts.ps1
```

Скрипт не чіпає `LegacyProjectStorageMigrator.java` та інші актуальні source files.
