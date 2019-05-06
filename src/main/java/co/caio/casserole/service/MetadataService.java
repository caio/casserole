package co.caio.casserole.service;

import co.caio.cerberus.db.RecipeMetadata;
import co.caio.cerberus.db.RecipeMetadataDatabase;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {

  private final RecipeMetadataDatabase metadataDatabase;

  public MetadataService(RecipeMetadataDatabase metadataDatabase) {
    this.metadataDatabase = metadataDatabase;
  }

  public Optional<RecipeMetadata> findById(long recipeId) {
    return metadataDatabase.findById(recipeId);
  }
}
