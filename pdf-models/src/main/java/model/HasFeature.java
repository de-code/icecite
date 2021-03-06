package model;

/**
 * Interface to indicate that an object represents an feature. 
 *
 * @author Claudius Korzen
 *
 */
public interface HasFeature {
  /**
   * Returns the represented feature.
   */
  public PdfFeature getFeature();
}
