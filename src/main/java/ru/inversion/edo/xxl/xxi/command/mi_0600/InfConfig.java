package ru.inversion.edo.xxl.xxi.command.mi_0600;

import ru.inversion.edo.xxl.xxi.repo.InfRole;

import javax.persistence.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Entity(name = "InfConfig")
@NamedNativeQuery(
   name = "",
   query= """
   select i.inf_id,
   MI_prp.get_Inf_Property( i.inf_id, 'ENABLED', '1') as enabled,
   MI_prp.get_Inf_Property( i.inf_id, 'SEND_DIR',null ) as work_dir,
   MI_prp.get_Inf_Property( i.inf_id, 'ZIP_COPY_DIR',null ) as zip_copy_dir,
   MI_prp.get_Inf_Property( i.inf_id, 'MAKE_ZIP_COPY', '0' ) as make_zip_copy,
   MI_prp.get_Inf_Property( i.inf_id, 'COLLECT_DELAY', '30' ) as collect_delay
   from xxi.mi_inf i where i.wsp_id = 600
   """
)
public class InfConfig {
   private int      infId  = 0;
   private boolean  enabled=true;
   private Path     workDir;
   private Duration collectDelay;
   private boolean  makeZipCopy=false;
   private Path     zipCopyDir;

   //private int      initiator_cd = -1;

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

   @Column(name = "make_zip_copy")
   public Integer getMakeZipCopy() {
      return this.makeZipCopy ? 1 : 0;
   }
   public void setMakeZipCopy(Integer v) {
      this.makeZipCopy = !(v == null || v == 0);
   }

   public boolean makeZipCopy()
   {
      return makeZipCopy;
   }

   @Column(name = "zip_copy_dir")
   public String getZipCopyDir() {
      return zipCopyDir == null ? null : zipCopyDir.toString();
   }
   public void setZipCopyDir( String v) {
      zipCopyDir = v == null ? null : Paths.get(v);
   }

   public Path zipCopyDir() {
      return zipCopyDir;
   }
/*
   @Column(name = "initiator_cd")
   public Integer getInitiatorCd() {
      return initiator_cd;
   }
   public void setInitiatorCd(Integer v) {
      this.infId = v == null ? -1 : v;
   }

   @Transient
   public InfRole role()
   {
      return  InfRole.of(initiator_cd);
   }
*/

}

