package ru.inversion.edo.xxl.xxi.command.mi_0600;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQuery;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Entity(name = "InfConfig")
@NamedNativeQuery(
   name = "",
   query= """
   select i.inf_id,
   MI_prp.get_Inf_Property( i.inf_id, 'FILE_ENABLED', '1') as enabled,
   MI_prp.get_Inf_Property( i.inf_id, 'FILE_SEND_DIR',null ) as work_dir,
   MI_prp.get_Inf_Property( i.inf_id, 'FILE_COLLECT_DELAY_MS', '30000' ) as collect_delay
   from xxi.mi_inf i where i.wsp_id = 600
   """
)
public class InfConfig {
   private int      infId;
   private boolean  enabled;
   private Path     workDir;
   private Duration collectDelay;

   @Id
   @Column(name = "inf_id")
   public Integer getInfId() {
      return infId == 0 ? null : infId;
   }
   public void setInfId(Integer id) {
      this.infId = id == null ? 0 : id;
   }

   public int infId() { return infId; }

   @Column(name = "enabled")
   public Integer getEnabled() {
      return this.enabled ? 1 : 0;
   }
   public void setEnabled(Integer v) {
      this.enabled = !(v == null || v == 0);
   }

   public boolean enabled()
   {
      return enabled;
   }

   @Column(name = "work_dir")
   public String getWorkDir() {
      return workDir == null ? null : workDir.toString();
   }
   public void setWorkDir( String v) {
      workDir = v == null ? null : Paths.get(v);
   }

   public Path workDir() {
      return workDir;
   }

   @Column(name = "collect_delay")
   public Long getCollectDelay() {
      return collectDelay == null ? null : collectDelay.getSeconds();
   }
   public void setCollectDelay(Long v) {
      collectDelay = v == null ? null : Duration.ofSeconds(v);
   }

   public Duration collectDelay() {
      return collectDelay;
   }

}

