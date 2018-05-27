/*
Copyright (c) 2016 James Ahlborn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.healthmarketscience.jackcess.impl.expr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import com.healthmarketscience.jackcess.expr.EvalContext;
import com.healthmarketscience.jackcess.expr.EvalException;
import com.healthmarketscience.jackcess.expr.Value;
import com.healthmarketscience.jackcess.impl.ColumnImpl;


/**
 *
 * @author James Ahlborn
 */
public class BuiltinOperators
{
  private static final String DIV_BY_ZERO = "/ by zero";

  private static final double MIN_INT = Integer.MIN_VALUE;
  private static final double MAX_INT = Integer.MAX_VALUE;

  public static final Value NULL_VAL = new BaseValue() {
    @Override public boolean isNull() {
      return true;
    }
    public Type getType() {
      return Type.NULL;
    }
    public Object get() {
      return null;
    }
  };
  // access seems to like -1 for true and 0 for false (boolean values are
  // basically an illusion)
  public static final Value TRUE_VAL = new LongValue(-1);
  public static final Value FALSE_VAL = new LongValue(0);
  public static final Value EMPTY_STR_VAL = new StringValue("");
  public static final Value ZERO_VAL = FALSE_VAL;

  public static final RoundingMode ROUND_MODE = RoundingMode.HALF_EVEN;
  private static final int MAX_NUMERIC_SCALE = 28;

  private enum CoercionType {
    SIMPLE(true, true), GENERAL(false, true), COMPARE(false, false);

    final boolean _preferTemporal;
    final boolean _allowCoerceStringToNum;

    private CoercionType(boolean preferTemporal,
                         boolean allowCoerceStringToNum) {
      _preferTemporal = preferTemporal;
      _allowCoerceStringToNum = allowCoerceStringToNum;
    }
  }

  private BuiltinOperators() {}

  // null propagation rules:
  // http://www.utteraccess.com/wiki/index.php/Nulls_And_Their_Behavior
  // https://theaccessbuddy.wordpress.com/2012/10/24/6-logical-operators-in-ms-access-that-you-must-know-operator-types-3-of-5/
  // - number ops
  // - comparison ops
  // - logical ops (some "special")
  //   - And - can be false if one arg is false
  //   - Or - can be true if one arg is true
  // - between, not, like, in
  // - *NOT* concal op '&'

  public static Value negate(EvalContext ctx, Value param1) {
    if(param1.isNull()) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = param1.getType();

    switch(mathType) {
    case DATE:
    case TIME:
    case DATE_TIME:
      // dates/times get converted to date doubles for arithmetic
      double result = -param1.getAsDouble();
      return toDateValue(ctx, mathType, result, param1, null);
    case LONG:
      return toValue(-param1.getAsLongInt());
    case DOUBLE:
      return toValue(-param1.getAsDouble());
    case STRING:
    case BIG_DEC:
      return toValue(param1.getAsBigDecimal().negate());
    default:
      throw new EvalException("Unexpected type " + mathType);
    }
  }

  public static Value add(EvalContext ctx, Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.SIMPLE);

    switch(mathType) {
    case STRING:
      // string '+' is a null-propagation (handled above) concat
      return nonNullConcat(param1, param2);
    case DATE:
    case TIME:
    case DATE_TIME:
      // dates/times get converted to date doubles for arithmetic
      double result = param1.getAsDouble() + param2.getAsDouble();
      return toDateValue(ctx, mathType, result, param1, param2);
    case LONG:
      return toValue(param1.getAsLongInt() + param2.getAsLongInt());
    case DOUBLE:
      return toValue(param1.getAsDouble() + param2.getAsDouble());
    case BIG_DEC:
      return toValue(param1.getAsBigDecimal().add(param2.getAsBigDecimal()));
    default:
      throw new EvalException("Unexpected type " + mathType);
    }
  }

  public static Value subtract(EvalContext ctx, Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.SIMPLE);

    switch(mathType) {
    // case STRING: break; unsupported
    case DATE:
    case TIME:
    case DATE_TIME:
      // dates/times get converted to date doubles for arithmetic
      double result = param1.getAsDouble() - param2.getAsDouble();
      return toDateValue(ctx, mathType, result, param1, param2);
    case LONG:
      return toValue(param1.getAsLongInt() - param2.getAsLongInt());
    case DOUBLE:
      return toValue(param1.getAsDouble() - param2.getAsDouble());
    case BIG_DEC:
      return toValue(param1.getAsBigDecimal().subtract(param2.getAsBigDecimal()));
    default:
      throw new EvalException("Unexpected type " + mathType);
    }
  }

  public static Value multiply(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.GENERAL);

    switch(mathType) {
    // case STRING: break; unsupported
    // case DATE: break; promoted to double
    // case TIME: break; promoted to double
    // case DATE_TIME: break; promoted to double
    case LONG:
      return toValue(param1.getAsLongInt() * param2.getAsLongInt());
    case DOUBLE:
      return toValue(param1.getAsDouble() * param2.getAsDouble());
    case BIG_DEC:
      return toValue(param1.getAsBigDecimal().multiply(param2.getAsBigDecimal()));
    default:
      throw new EvalException("Unexpected type " + mathType);
    }
  }

  public static Value divide(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.GENERAL);

    switch(mathType) {
    // case STRING: break; unsupported
    // case DATE: break; promoted to double
    // case TIME: break; promoted to double
    // case DATE_TIME: break; promoted to double
    case LONG:
      int lp1 = param1.getAsLongInt();
      int lp2 = param2.getAsLongInt();
      if((lp1 % lp2) == 0) {
        return toValue(lp1 / lp2);
      }
      return toValue((double)lp1 / (double)lp2);
    case DOUBLE:
      double d2 = param2.getAsDouble();
      if(d2 == 0.0d) {
        throw new ArithmeticException(DIV_BY_ZERO);
      }
      return toValue(param1.getAsDouble() / d2);
    case BIG_DEC:
      return toValue(divide(param1.getAsBigDecimal(), param2.getAsBigDecimal()));
    default:
      throw new EvalException("Unexpected type " + mathType);
    }
  }

  public static Value intDivide(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.GENERAL);
    if(mathType == Value.Type.STRING) {
      throw new EvalException("Unexpected type " + mathType);
    }
    return toValue(param1.getAsLongInt() / param2.getAsLongInt());
  }

  public static Value exp(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.GENERAL);

    // jdk only supports general pow() as doubles, let's go with that
    double result = Math.pow(param1.getAsDouble(), param2.getAsDouble());

    // attempt to convert integral types back to integrals if possible
    if((mathType == Value.Type.LONG) && isIntegral(result)) {
      return toValue((int)result);
    }

    return toValue(result);
  }

  public static Value mod(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    Value.Type mathType = getMathTypePrecedence(param1, param2,
                                                CoercionType.GENERAL);

    if(mathType == Value.Type.STRING) {
      throw new EvalException("Unexpected type " + mathType);
    }
    return toValue(param1.getAsLongInt() % param2.getAsLongInt());
  }

  public static Value concat(Value param1, Value param2) {

    // note, this op converts null to empty string
    if(param1.isNull()) {
      param1 = EMPTY_STR_VAL;
    }

    if(param2.isNull()) {
      param2 = EMPTY_STR_VAL;
    }

    return nonNullConcat(param1, param2);
  }

  private static Value nonNullConcat(Value param1, Value param2) {
    return toValue(param1.getAsString().concat(param2.getAsString()));
  }

  public static Value not(Value param1) {
    if(param1.isNull()) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(!param1.getAsBoolean());
  }

  public static Value lessThan(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(nonNullCompareTo(param1, param2) < 0);
  }

  public static Value greaterThan(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(nonNullCompareTo(param1, param2) > 0);
  }

  public static Value lessThanEq(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(nonNullCompareTo(param1, param2) <= 0);
  }

  public static Value greaterThanEq(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(nonNullCompareTo(param1, param2) >= 0);
  }

  public static Value equals(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(nonNullCompareTo(param1, param2) == 0);
  }

  public static Value notEquals(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(nonNullCompareTo(param1, param2) != 0);
  }

  public static Value and(Value param1, Value param2) {

    // "and" uses short-circuit logic

    if(param1.isNull()) {
      return NULL_VAL;
    }

    boolean b1 = param1.getAsBoolean();
    if(!b1) {
      return FALSE_VAL;
    }

    if(param2.isNull()) {
      return NULL_VAL;
    }

    return toValue(param2.getAsBoolean());
  }

  public static Value or(Value param1, Value param2) {

    // "or" uses short-circuit logic

    if(param1.isNull()) {
      return NULL_VAL;
    }

    boolean b1 = param1.getAsBoolean();
    if(b1) {
      return TRUE_VAL;
    }

    if(param2.isNull()) {
      return NULL_VAL;
    }

    return toValue(param2.getAsBoolean());
  }

  public static Value eqv(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    boolean b1 = param1.getAsBoolean();
    boolean b2 = param2.getAsBoolean();

    return toValue(b1 == b2);
  }

  public static Value xor(Value param1, Value param2) {
    if(anyParamIsNull(param1, param2)) {
      // null propagation
      return NULL_VAL;
    }

    boolean b1 = param1.getAsBoolean();
    boolean b2 = param2.getAsBoolean();

    return toValue(b1 ^ b2);
  }

  public static Value imp(Value param1, Value param2) {

    // "imp" uses short-circuit logic

    if(param1.isNull()) {
      if(param2.isNull() || !param2.getAsBoolean()) {
        // null propagation
        return NULL_VAL;
      }

      return TRUE_VAL;
    }

    boolean b1 = param1.getAsBoolean();
    if(!b1) {
      return TRUE_VAL;
    }

    if(param2.isNull()) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(param2.getAsBoolean());
  }

  public static Value isNull(Value param1) {
    return toValue(param1.isNull());
  }

  public static Value isNotNull(Value param1) {
    return toValue(!param1.isNull());
  }

  public static Value like(Value param1, Pattern pattern) {
    if(param1.isNull()) {
      // null propagation
      return NULL_VAL;
    }

    return toValue(pattern.matcher(param1.getAsString()).matches());
  }

  public static Value between(Value param1, Value param2, Value param3) {
    // null propagate any param.  uses short circuit eval of params
    if(anyParamIsNull(param1, param2, param3)) {
      // null propagation
      return NULL_VAL;
    }

    // the between values can be in either order!?!
    Value min = param2;
    Value max = param3;
    Value gt = greaterThan(min, max);
    if(gt.getAsBoolean()) {
      min = param3;
      max = param2;
    }

    return and(greaterThanEq(param1, min), lessThanEq(param1, max));
  }

  public static Value notBetween(Value param1, Value param2, Value param3) {
    return not(between(param1, param2, param3));
  }

  public static Value in(Value param1, Value[] params) {

    // null propagate any param.  uses short circuit eval of params
    if(param1.isNull()) {
      // null propagation
      return NULL_VAL;
    }

    for(Value val : params) {
      if(val.isNull()) {
        continue;
      }

      Value eq = equals(param1, val);
      if(eq.getAsBoolean()) {
        return TRUE_VAL;
      }
    }

    return FALSE_VAL;
  }

  public static Value notIn(Value param1, Value[] params) {
    return not(in(param1, params));
  }


  private static boolean anyParamIsNull(Value param1, Value param2) {
    return (param1.isNull() || param2.isNull());
  }

  private static boolean anyParamIsNull(Value param1, Value param2,
                                        Value param3) {
    return (param1.isNull() || param2.isNull() || param3.isNull());
  }

  protected static int nonNullCompareTo(
      Value param1, Value param2)
  {
    // note that comparison does not do string to num coercion
    Value.Type compareType = getMathTypePrecedence(param1, param2,
                                                   CoercionType.COMPARE);

    switch(compareType) {
    case STRING:
      // string comparison is only valid if _both_ params are strings
      if(param1.getType() != param2.getType()) {
        throw new EvalException("Unexpected type " + compareType);
      }
      return param1.getAsString().compareToIgnoreCase(param2.getAsString());
    // case DATE: break; promoted to double
    // case TIME: break; promoted to double
    // case DATE_TIME: break; promoted to double
    case LONG:
      return param1.getAsLongInt().compareTo(param2.getAsLongInt());
    case DOUBLE:
      return param1.getAsDouble().compareTo(param2.getAsDouble());
    case BIG_DEC:
      return param1.getAsBigDecimal().compareTo(param2.getAsBigDecimal());
    default:
      throw new EvalException("Unexpected type " + compareType);
    }
  }

  public static Value toValue(boolean b) {
    return (b ? TRUE_VAL : FALSE_VAL);
  }

  public static Value toValue(String s) {
    return new StringValue(s);
  }

  public static Value toValue(int i) {
    return new LongValue(i);
  }

  public static Value toValue(Integer i) {
    return new LongValue(i);
  }

  public static Value toValue(float f) {
    return new DoubleValue((double)f);
  }

  public static Value toValue(double s) {
    return new DoubleValue(s);
  }

  public static Value toValue(Double s) {
    return new DoubleValue(s);
  }

  public static Value toValue(BigDecimal s) {
    return new BigDecimalValue(normalize(s));
  }

  public static Value toValue(Value.Type type, double dd, DateFormat fmt) {
    return toValue(type, new Date(ColumnImpl.fromDateDouble(
                                      dd, fmt.getCalendar())), fmt);
  }

  public static Value toValue(EvalContext ctx, Value.Type type, Date d) {
    return toValue(type, d, getDateFormatForType(ctx, type));
  }

  public static Value toValue(Value.Type type, Date d, DateFormat fmt) {
    switch(type) {
    case DATE:
      return new DateValue(d, fmt);
    case TIME:
      return new TimeValue(d, fmt);
    case DATE_TIME:
      return new DateTimeValue(d, fmt);
    default:
      throw new EvalException("Unexpected date/time type " + type);
    }
  }

  static Value toDateValue(EvalContext ctx, Value.Type type, double v,
                           Value param1, Value param2)
  {
    DateFormat fmt = null;
    if((param1 instanceof BaseDateValue) && (param1.getType() == type)) {
      fmt = ((BaseDateValue)param1).getFormat();
    } else if((param2 instanceof BaseDateValue) && (param2.getType() == type)) {
      fmt = ((BaseDateValue)param2).getFormat();
    } else {
      fmt = getDateFormatForType(ctx, type);
    }

    Date d = new Date(ColumnImpl.fromDateDouble(v, fmt.getCalendar()));

    return toValue(type, d, fmt);
  }

  static DateFormat getDateFormatForType(EvalContext ctx, Value.Type type) {
      String fmtStr = null;
      switch(type) {
      case DATE:
        fmtStr = ctx.getTemporalConfig().getDefaultDateFormat();
        break;
      case TIME:
        fmtStr = ctx.getTemporalConfig().getDefaultTimeFormat();
        break;
      case DATE_TIME:
        fmtStr = ctx.getTemporalConfig().getDefaultDateTimeFormat();
        break;
      default:
        throw new EvalException("Unexpected date/time type " + type);
      }
      return ctx.createDateFormat(fmtStr);
  }

  private static Value.Type getMathTypePrecedence(
      Value param1, Value param2, CoercionType cType)
  {
    Value.Type t1 = param1.getType();
    Value.Type t2 = param2.getType();

    // note: for general math, date/time become double

    if(t1 == t2) {

      if(!cType._preferTemporal && t1.isTemporal()) {
        return t1.getPreferredNumericType();
      }

      return t1;
    }

    if((t1 == Value.Type.STRING) || (t2 == Value.Type.STRING)) {

      if(cType._allowCoerceStringToNum) {
        // see if this is mixed string/numeric and the string can be coerced
        // to a number
        Value.Type numericType = coerceStringToNumeric(param1, param2, cType);
        if(numericType != null) {
          // string can be coerced to number
          return numericType;
        }
      }

      // string always wins
      return Value.Type.STRING;
    }

    // for "simple" math, keep as date/times
    if(cType._preferTemporal &&
       (t1.isTemporal() || t2.isTemporal())) {
      return (t1.isTemporal() ?
              (t2.isTemporal() ?
               // for mixed temporal types, always go to date/time
               Value.Type.DATE_TIME : t1) :
              t2);
    }

    return getPreferredNumericType(t1.getPreferredNumericType(),
                                   t2.getPreferredNumericType());
  }

  private static Value.Type getPreferredNumericType(Value.Type t1, Value.Type t2)
  {
    // if both types are integral, choose "largest"
    if(t1.isIntegral() && t2.isIntegral()) {
      return max(t1, t2);
    }

    // choose largest relevant floating-point type
    return max(t1.getPreferredFPType(), t2.getPreferredFPType());
  }

  private static Value.Type coerceStringToNumeric(
      Value param1, Value param2, CoercionType cType) {
    Value.Type t1 = param1.getType();
    Value.Type t2 = param2.getType();

    Value.Type prefType = null;
    Value strParam = null;
    if(t1.isNumeric()) {
      prefType = t1;
      strParam = param2;
    } else if(t2.isNumeric()) {
      prefType = t2;
      strParam = param1;
    } else if(t1.isTemporal()) {
      prefType = (cType._preferTemporal ? t1 : t1.getPreferredNumericType());
      strParam = param2;
    } else if(t2.isTemporal()) {
      prefType = (cType._preferTemporal ? t2 : t2.getPreferredNumericType());
      strParam = param1;
    } else {
      // no numeric type involved
      return null;
    }

    try {
      // see if string can be coerced to a number
      BigDecimal num = strParam.getAsBigDecimal();
      if(prefType.isNumeric()) {
        // re-evaluate the numeric type choice based on the type of the parsed
        // number
        Value.Type numType = ((num.scale() > 0) ?
                              Value.Type.BIG_DEC : Value.Type.LONG);
        prefType = getPreferredNumericType(numType, prefType);
      }
      return prefType;
    } catch(NumberFormatException ignored) {
      // not a number
    }

    return null;
  }

  private static Value.Type max(Value.Type t1, Value.Type t2) {
    return ((t1.compareTo(t2) > 0) ? t1 : t2);
  }

  static BigDecimal divide(BigDecimal num, BigDecimal denom) {
    return num.divide(denom, MAX_NUMERIC_SCALE, ROUND_MODE);
  }

  static boolean isIntegral(double d) {
    double id = Math.rint(d);
    return ((d == id) && (d >= MIN_INT) && (d <= MAX_INT) &&
            !Double.isInfinite(d) && !Double.isNaN(d));
  }

  /**
   * Converts the given BigDecimal to the minimal scale >= 0;
   */
  static BigDecimal normalize(BigDecimal bd) {
    if(bd.scale() == 0) {
      return bd;
    }
    // handle a bug in the jdk which doesn't strip zero values
    if(bd.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    bd = bd.stripTrailingZeros();
    if(bd.scale() < 0) {
      bd = bd.setScale(0);
    }
    return bd;
  }
}
