package uu.game.main.api.dto;



import static uu.app.datastore.domain.PageInfo.DEFAULT_PAGE_INDEX;
import static uu.app.datastore.domain.PageInfo.DEFAULT_PAGE_SIZE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import uu.app.validation.ValidationType;
import uu.app.workspace.dto.common.PageInfoDto;

@ValidationType("scoreListDtoInType")
public final class ScoreListDtoIn {

  private PageInfoDto pageInfo;

  public ScoreListDtoIn() {
    pageInfo = new PageInfoDto();
    pageInfo.setPageIndex(DEFAULT_PAGE_INDEX);
    pageInfo.setPageSize(DEFAULT_PAGE_SIZE);
  }

  public PageInfoDto getPageInfo() {
    return pageInfo;
  }

  public void setPageInfo(PageInfoDto pageInfo) {
    this.pageInfo = pageInfo;
  }

  @Override
  public String toString() {
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    try {
      return ow.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      return super.toString();
    }
  }

}
