package ru.inversion.edo.xxl.xxi.command.mi_0600;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQuery;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Настройки вида сведений MI_0600.
 *
 * Источник настроек: MI_INF и MI_PRP. Конфигурация исходящего захвата
 * не дублируется в application.yml или Spring Environment.
 *
 * Если CAPTURE_PREDICATE отсутствует, захват документов для вида
 * сведений не выполняется, даже если ENABLED=1.
 */
@Entity(name = "InfConfig")
@NamedNativeQuery(
      name = "",
      query = """
            SELECT i.inf_id,
                   MI_prp.get_Inf_Property(i.inf_id, 'ENABLED', '1') AS enabled,
                   MI_prp.get_Inf_Property(i.inf_id, 'SEND_DIR', NULL) AS work_dir,
                   MI_prp.get_Inf_Property(i.inf_id, 'ZIP_COPY_DIR', NULL) AS zip_copy_dir,
                   MI_prp.get_Inf_Property(i.inf_id, 'MAKE_ZIP_COPY', '0') AS make_zip_copy,
                   MI_prp.get_Inf_Property(i.inf_id, 'COLLECT_DELAY', '30')::numeric AS collect_delay,
                   MI_prp.get_Inf_Property(i.inf_id, 'PROCESSED_KEEP_DAYS', '7')::numeric AS processed_keep_days,
                   MI_prp.get_Inf_Property(i.inf_id, 'CAPTURE_PREDICATE', NULL) AS capture_predicate,
                   MI_prp.get_Inf_Property(i.inf_id, 'CAPTURE_MAX_FILES', '10')::numeric AS capture_max_files,
                   MI_prp.get_Inf_Property(i.inf_id, 'CAPTURE_MAX_XML_BYTES', '33554432')::numeric AS capture_max_xml_bytes,
                   MI_prp.get_Inf_Property(i.inf_id, 'CAPTURE_MAX_ZIP_BYTES', '33554432')::numeric AS capture_max_zip_bytes,
                   MI_prp.get_Inf_Property(i.inf_id, 'CAPTURE_MAX_BATCHES', '10')::numeric AS capture_max_batches
              FROM xxi.mi_inf i
             WHERE i.wsp_id = 600
            """
)
public class InfConfig
{
   private int infId;
   private boolean enabled = true;

   // Совместимость с прежней конфигурацией файлового обмена.
   // Новый исходящий захват из NI_OUTFILES эти параметры не использует.
   private Path workDir;
   private Path zipCopyDir;
   private Duration collectDelay;
   private boolean makeZipCopy;
   private int processedKeepDays = 7;

   // Настройки регистрации XML из NI_OUTFILES.
   private String capturePredicate;
   private int captureMaxFiles = 10;
   private long captureMaxXmlBytes = 32L * 1024 * 1024;
   private int captureMaxZipBytes = 32 * 1024 * 1024;
   private int captureMaxBatches = 10;

   @Id
   @Column(name = "inf_id")
   public Integer getInfId() { return infId == 0 ? null : infId; }
   public void setInfId(Integer value) { infId = value == null ? 0 : value; }
   public int infId() { return infId; }

   @Column(name = "enabled")
   public Integer getEnabled() { return enabled ? 1 : 0; }
   public void setEnabled(Integer value) { enabled = value != null && value != 0; }
   public boolean enabled() { return enabled; }

   @Column(name = "work_dir")
   public String getWorkDir() { return workDir == null ? null : workDir.toString(); }
   public void setWorkDir(String value) { workDir = value == null ? null : Paths.get(value); }
   public Path workDir() { return workDir; }

   @Column(name = "zip_copy_dir")
   public String getZipCopyDir() { return zipCopyDir == null ? null : zipCopyDir.toString(); }
   public void setZipCopyDir(String value) { zipCopyDir = value == null ? null : Paths.get(value); }
   public Path zipCopyDir() { return zipCopyDir; }

   @Column(name = "collect_delay")
   public Long getCollectDelay() { return collectDelay == null ? null : collectDelay.getSeconds(); }
   public void setCollectDelay(Long value) { collectDelay = value == null ? null : Duration.ofSeconds(value); }
   public Duration collectDelay() { return collectDelay; }

   @Column(name = "make_zip_copy")
   public Integer getMakeZipCopy() { return makeZipCopy ? 1 : 0; }
   public void setMakeZipCopy(Integer value) { makeZipCopy = value != null && value != 0; }
   public boolean makeZipCopy() { return makeZipCopy; }

   @Column(name = "processed_keep_days")
   public Integer getProcessedKeepDays() { return processedKeepDays; }
   public void setProcessedKeepDays(Integer value) { processedKeepDays = value == null ? 7 : value; }

   @Column(name = "capture_predicate")
   public String getCapturePredicate() { return capturePredicate; }
   public void setCapturePredicate(String value) { capturePredicate = value; }
   public String capturePredicate() { return capturePredicate; }

   @Column(name = "capture_max_files")
   public Integer getCaptureMaxFiles() { return captureMaxFiles; }
   public void setCaptureMaxFiles(Integer value) { captureMaxFiles = value == null ? 10 : value; }
   public int captureMaxFiles() { return captureMaxFiles; }

   @Column(name = "capture_max_xml_bytes")
   public Long getCaptureMaxXmlBytes() { return captureMaxXmlBytes; }
   public void setCaptureMaxXmlBytes(Long value) { captureMaxXmlBytes = value == null ? 32L * 1024 * 1024 : value; }
   public long captureMaxXmlBytes() { return captureMaxXmlBytes; }

   @Column(name = "capture_max_zip_bytes")
   public Integer getCaptureMaxZipBytes() { return captureMaxZipBytes; }
   public void setCaptureMaxZipBytes(Integer value) { captureMaxZipBytes = value == null ? 32 * 1024 * 1024 : value; }
   public int captureMaxZipBytes() { return captureMaxZipBytes; }

   @Column(name = "capture_max_batches")
   public Integer getCaptureMaxBatches() { return captureMaxBatches; }
   public void setCaptureMaxBatches(Integer value) { captureMaxBatches = value == null ? 10 : value; }
   public int captureMaxBatches() { return captureMaxBatches; }
}
