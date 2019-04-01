package co.caio.casserole;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class RecipeMetadataService {

  private final RecipeMetadataDatabase metadataDatabase;

  RecipeMetadataService(RecipeMetadataDatabase metadataDatabase) {
    this.metadataDatabase = metadataDatabase;
  }

  Optional<RecipeMetadata> findById(long recipeId) {
    return metadataDatabase.findById(recipeId);
  }
}
