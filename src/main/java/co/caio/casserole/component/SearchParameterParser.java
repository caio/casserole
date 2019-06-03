package co.caio.casserole.component;

import co.caio.casserole.index.Facet.DietOption;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.DietSpec;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class SearchParameterParser {
  private final int pageSize;

  public SearchParameterParser(@Qualifier("searchPageSize") int pageSize) {
    this.pageSize = pageSize;
  }

  public SearchQuery buildQuery(Map<String, String> params) {
    try {
      return _buildQuery(params);
    } catch (SearchParameterException rethrown) {
      throw rethrown;
    } catch (Exception wrapped) {
      throw new SearchParameterException(wrapped);
    }
  }

  private SearchQuery _buildQuery(Map<String, String> params) {
    var builder =
        new SearchQuery.Builder()
            .fulltext("") // Overwritten if &q=; is provided
            .maxResults(pageSize)
            .maxFacets(5);

    // TODO jdk12 switches plz
    params.forEach(
        (param, value) -> {
          switch (param) {
            case "q":
              builder.fulltext(value);
              break;
            case "sort":
              builder.sort(parseSortOrder(value));
              break;
            case "ni":
              builder.numIngredients(parseRange(value));
              break;
            case "tt":
              builder.totalTime(parseRange(value));
              break;
            case "n_k":
              builder.calories(parseRange(value));
              break;
            case "n_f":
              builder.fatContent(parseRange(value));
              break;
            case "n_c":
              builder.carbohydrateContent(parseRange(value));
              break;
            case "diet":
              builder.diet(parseDiet(value));
              break;
            case "page":
              // page starts from 1, not 0
              var pageNumber = parseUnsignedInt(value);
              if (pageNumber > 30) {
                throw new SearchParameterException(
                    "For performance reasons, viewing pages 31+ is not allowed.");
              }
              builder.offset((pageNumber - 1) * pageSize);
              break;
            default:
              throw new SearchParameterException("Unknown parameter " + param);
          }
        });

    return builder.build();
  }

  private int parseUnsignedInt(String value) {
    try {
      return Integer.parseUnsignedInt(value);
    } catch (NumberFormatException ex) {
      throw new SearchParameterException("Can't parse a number >= 0 from " + value);
    }
  }

  public SortOrder parseSortOrder(String order) {
    switch (order) {
      case "cook_time":
        return SortOrder.COOK_TIME;
      case "total_time":
        return SortOrder.TOTAL_TIME;
      case "prep_time":
        return SortOrder.PREP_TIME;
      case "relevance":
        return SortOrder.RELEVANCE;
      case "num_ingredients":
        return SortOrder.NUM_INGREDIENTS;
      case "calories":
        return SortOrder.CALORIES;
    }
    throw new SearchParameterException("Invalid sort order: " + order);
  }

  public DietSpec parseDiet(String input) {
    try {
      String name = input;
      float threshold = 1.0F;

      var idx = input.indexOf(':');

      if (idx != -1) {
        name = input.substring(0, idx);
        threshold = Float.parseFloat(input.substring(idx + 1));
      }

      if (DietOption.indexKeyIsKnown(name)) {
        return DietSpec.of(name, threshold);
      }

      throw new RuntimeException();
    } catch (Exception swallowed) {
      throw new SearchParameterException("Invalid diet: " + input);
    }
  }

  public RangedSpec parseRange(String input) {
    try {

      int start, end;
      var idx = input.indexOf(',');

      if (idx == -1) {
        start = 0;
        end = Integer.parseUnsignedInt(input);
      } else {
        start = Integer.parseUnsignedInt(input, 0, idx, 10);
        end = Integer.parseUnsignedInt(input, idx + 1, input.length(), 10);
      }

      return RangedSpec.of(start, end == 0 ? Integer.MAX_VALUE : end);
    } catch (Exception swallowed) {
      throw new SearchParameterException("Invalid range: " + input);
    }
  }

  public static class SearchParameterException extends RuntimeException {
    SearchParameterException(String message) {
      super(message);
    }

    SearchParameterException(Throwable throwable) {
      super(throwable);
    }
  }
}
