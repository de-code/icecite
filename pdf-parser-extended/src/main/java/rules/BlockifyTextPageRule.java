package rules;

import static model.SweepDirection.HorizontalSweepDirection.TOP_TO_BOTTOM;
import static model.SweepDirection.VerticalSweepDirection.LEFT_TO_RIGHT;

import java.util.List;

import de.freiburg.iif.model.Rectangle;
import de.freiburg.iif.model.simple.SimpleRectangle;
import model.Characters;
import model.PdfArea;
import model.PdfCharacter;
import model.PdfDocument;
import model.PdfElement;
import model.PdfPage;
import model.SweepDirection.HorizontalSweepDirection;
import model.SweepDirection.VerticalSweepDirection;

/**
 * The rules to blockify a page into several "blocks".
 *
 * @author Claudius Korzen
 */
public class BlockifyTextPageRule implements BlockifyRule {
  /** The sweep direction for the horizontal lane. */
  protected HorizontalSweepDirection horizontalLaneSweepDirection;
  /** The sweep direction for the vertical lane. */
  protected VerticalSweepDirection verticalLaneSweepDirection;

  /**
   * The default constructor.
   */
  public BlockifyTextPageRule() {
    this.horizontalLaneSweepDirection = TOP_TO_BOTTOM;
    this.verticalLaneSweepDirection = LEFT_TO_RIGHT;
  }

  /**
   * The default constructor.
   */
  public BlockifyTextPageRule(HorizontalSweepDirection horizontalSweepDirection,
      VerticalSweepDirection verticalSweepDirection) {
    this.horizontalLaneSweepDirection = horizontalSweepDirection;
    this.verticalLaneSweepDirection = verticalSweepDirection;
  }

  @Override
  public VerticalSweepDirection getVerticalLaneSweepDirection() {
    return this.verticalLaneSweepDirection;
  }

  @Override
  public float getVerticalLaneWidth(PdfArea area) {
    // Obtaining a suitable width of vertical lanes is a complex thing.
    // Normally, columns are separated clearly by a vertical lane, such that we
    // can define a proper width for it (for example based on the most common
    // character width in the area. But from time to time, some text, figures or
    // tables may extend into column lanes which breaks our approach that a 
    // lane must be empty to be a valid column lane.
    // Consequently, the width of a valid vertical lane must generally exceed
    // the most common character width 2 times. But if the height of area is
    // large enough (larger than 0.5 * pagewidth), we allow a very small width 
    // (0.1). 

    PdfPage page = area.getPage();
    float pageHeight = page.getRectangle().getHeight();
    float areaHeight = area.getRectangle().getHeight();

    if (areaHeight < 0.5f * pageHeight) {
      PdfDocument doc = area.getPdfDocument();

      float docWidths = doc.getDimensionStatistics().getMostCommonWidth();
      float pageWidths = area.getDimensionStatistics().getMostCommonWidth();

      return 2f * Math.max(docWidths, pageWidths);
    }

    return .1f;
  }

  @Override
  public boolean isValidVerticalLane(PdfArea area, Rectangle lane) {
    float leftHalfWidth = computeWidthOfLeftHalf(area, lane);
    float rightHalfWidth = computeWidthOfRightHalf(area, lane);

    PdfDocument doc = area.getPdfDocument();
    float mostCommonWidth = doc.getDimensionStatistics().getMostCommonWidth();

    // Don't allow the lane if resulting subareas are too slim.
    if (leftHalfWidth < 25 * mostCommonWidth
        || rightHalfWidth < 25 * mostCommonWidth) {
      return false;
    }

    float leftHalfMathRatio = computeMathRatioOfLeftHalf(area, lane);
    float rightHalfMathRatio = computeMathRatioOfRightHalf(area, lane);

    // Don't allow the lane if resulting subareas contain too many math symbols.
    if (leftHalfMathRatio > 0.75f || rightHalfMathRatio > 0.75f) {
      return false;
    }

    boolean overlapsChars = !area.getTextCharactersOverlapping(lane).isEmpty();

    // The lane isn't valid, if it overlaps any elements.
    if (overlapsChars) {
      return false;
    }

    // TODO: Do we need this piece of code?
    //    // The lane is valid, if it is larger than "the default width" 
    //    // (see getVerticalLaneWidth).
    //    if (MathUtils.isLarger(lane.getWidth(), 0.1f, 0.01f)) {
    //      return true;
    //    }

    // The lane doesn't overlap elements and is of default width. Check if it
    // separates consecutive characters.   
    return !separatesConsecutiveCharacters(area, lane);
  }

  @Override
  public HorizontalSweepDirection getHorizontalLaneSweepDirection() {
    return this.horizontalLaneSweepDirection;
  }

  @Override
  public float getHorizontalLaneHeight(PdfArea area) {
    // Ideally, we should use most common values here. But doing so fails
    // for grotoap-20902190.pdf - because of so many dots on page 3 and 4.
    PdfDocument doc = area.getPdfDocument();
    //  float docHeights = doc.getDimensionStatistics().getMostCommonHeight();
    //  float pageHeights = area.getDimensionStatistics().getMostCommonHeight();

    // System.out.println(Math.max(docHeights, pageHeights) + " " +
    // doc.getEstimatedLinePitch());

    return 2f * doc.getEstimatedLinePitch();
    // return 1.5f * Math.max(docHeights, pageHeights);
  }

  @Override
  public boolean isValidHorizontalLane(PdfArea area, Rectangle lane) {
    return area.getTextCharactersOverlapping(lane).isEmpty();
    
//    List<PdfCharacter> chars = area.getTextCharactersOverlapping(lane);
//
//    for (PdfCharacter character : chars) {
//      if (Characters.isLatinLetter(character)
//          && !Characters.isAscender(character)
//          && !Characters.isDescender(character)) {
//        return false;
//      }
//    }
//
//    return true;
  }

  // ===========================================================================

  protected float computeWidthOfLeftHalf(PdfArea area, Rectangle lane) {
    Rectangle leftHalf = new SimpleRectangle();
    leftHalf.setMinX(area.getRectangle().getMinX());
    leftHalf.setMinY(area.getRectangle().getMinY());
    leftHalf.setMaxX(lane.getRectangle().getMinX());
    leftHalf.setMaxY(area.getRectangle().getMaxY());
    List<PdfElement> leftHalfElements = area.getElementsOverlapping(leftHalf);
    Rectangle boundBox = SimpleRectangle.computeBoundingBox(leftHalfElements);
    return boundBox.getWidth();
  }

  protected float computeWidthOfRightHalf(PdfArea area, Rectangle lane) {
    Rectangle rightHalf = new SimpleRectangle();
    rightHalf.setMinX(lane.getRectangle().getMaxX());
    rightHalf.setMinY(area.getRectangle().getMinY());
    rightHalf.setMaxX(area.getRectangle().getMaxX());
    rightHalf.setMaxY(area.getRectangle().getMaxY());
    List<PdfElement> rightHalfElements = area.getElementsOverlapping(rightHalf);
    Rectangle boundBox = SimpleRectangle.computeBoundingBox(rightHalfElements);
    return boundBox.getWidth();
  }

  protected float computeMathRatioOfLeftHalf(PdfArea area, Rectangle lane) {
    Rectangle leftHalf = new SimpleRectangle();
    leftHalf.setMinX(area.getRectangle().getMinX());
    leftHalf.setMinY(area.getRectangle().getMinY());
    leftHalf.setMaxX(lane.getRectangle().getMinX());
    leftHalf.setMaxY(area.getRectangle().getMaxY());
    List<PdfCharacter> leftHalfChars =
        area.getTextCharactersOverlapping(leftHalf);

    float numNonMathWords = 0;
    float numMathWords = 0;

    for (PdfCharacter character : leftHalfChars) {
      if (Characters.isMathSymbol(character) || character.isSubScript()
          || character.isSuperScript()) {
        numMathWords++;
      } else {
        numNonMathWords++;
      }
    }

    return numMathWords / (numNonMathWords + numMathWords);
  }

  protected float computeMathRatioOfRightHalf(PdfArea area, Rectangle lane) {
    Rectangle rightHalf = new SimpleRectangle();
    rightHalf.setMinX(lane.getRectangle().getMaxX());
    rightHalf.setMinY(area.getRectangle().getMinY());
    rightHalf.setMaxX(area.getRectangle().getMaxX());
    rightHalf.setMaxY(area.getRectangle().getMaxY());
    List<PdfCharacter> rightHalfChars =
        area.getTextCharactersOverlapping(rightHalf);

    float numNonMathWords = 0;
    float numMathWords = 0;

    for (PdfCharacter character : rightHalfChars) {
      if (Characters.isMathSymbol(character) || character.isSubScript()
          || character.isSuperScript()) {
        numMathWords++;
      } else {
        numNonMathWords++;
      }
    }

    return numMathWords / (numNonMathWords + numMathWords);
  }

  /**
   * Returns true if the given lane separates consecutive characters. 
   * 
   * To be more exact, this method returns true if there is an element A to the 
   * left of the lane and and an element B to the right of the lane where 
   * B.extractionOrderNumber == A.extractionOrderNumber and A and B overlaps
   * vertically. 
   */
  protected boolean separatesConsecutiveCharacters(PdfArea area,
      Rectangle lane) {
    float mcWidth = area.getDimensionStatistics().getMostCommonWidth();
    float mcHeight = area.getDimensionStatistics().getMostCommonHeight();

    // Define the search area to the left of the lane.
    Rectangle leftArea = new SimpleRectangle();
    leftArea.setMinX(area.getRectangle().getMinX() - 5 * mcWidth);
    leftArea.setMinY(lane.getMinY());
    leftArea.setMaxX(lane.getMaxX());
    leftArea.setMaxY(lane.getMaxY());

    // Obtain the characters overlapping the left area.
    List<PdfCharacter> lChars = area.getTextCharactersOverlapping(leftArea);

    // Iterate through the characters to the left of the lane.
    for (int i = 0; i < lChars.size(); i++) {
      PdfCharacter lChar = lChars.get(i);

      // Define the search area to the right of the lane. 
      Rectangle rightArea = new SimpleRectangle();
      rightArea.setMinX(lane.getMinX());
      // Restrict the maxX to the right border of the area.
      rightArea.setMaxX(area.getRectangle().getMaxX());
      // Restrict the y dimensions to those of the left character.
      rightArea.setMinY(lChar.getRectangle().getMinY() - mcHeight);
      rightArea.setMaxY(lChar.getRectangle().getMaxY() + mcHeight);

      // Obtain the characters overlapping the right area.
      List<PdfCharacter> rChars = area.getTextCharactersOverlapping(rightArea);

      // Iterate through the characters to the right of the lane and check, if
      // there is an character 'rightChar' with 
      // rightChar.extractionOrderNumber == leftChar.extractionOrderNumber + 1
      for (PdfCharacter rChar : rChars) {
        int leftExtractionOrderNumber = lChar.getExtractionOrderNumber();
        int rightExtractionOrderNumber = rChar.getExtractionOrderNumber();

        if (rightExtractionOrderNumber == leftExtractionOrderNumber + 1
            || rightExtractionOrderNumber == leftExtractionOrderNumber + 2) {
          // There is a consecutive char pair that is divided by the lane.
          return true;
        }
      }
    }
    return false;
  }
}
