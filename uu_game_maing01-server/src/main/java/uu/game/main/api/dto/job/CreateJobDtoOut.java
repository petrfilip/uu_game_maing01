package uu.game.main.api.dto.job;

import uu.app.dto.AbstractDtoOut;

/**
 * DTO contains basic information about created job.
 */
public final class CreateJobDtoOut extends AbstractDtoOut {

  private ActiveJob job;

  public ActiveJob getJob() {
    return job;
  }

  public void setJob(ActiveJob identifiedJob) {
    this.job = identifiedJob;
  }

  public CreateJobDtoOut() {}

  public CreateJobDtoOut(ActiveJob job) {
    this.job = job;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("CreateJobDtoOut{");
    sb.append("job=").append(job);
    sb.append('}');
    return sb.toString();
  }
}

