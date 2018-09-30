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
import java.util.HashMap;
import java.util.Map;

import com.healthmarketscience.jackcess.expr.EvalContext;
import com.healthmarketscience.jackcess.expr.EvalException;
import com.healthmarketscience.jackcess.expr.Function;
import com.healthmarketscience.jackcess.expr.FunctionLookup;
import com.healthmarketscience.jackcess.expr.Value;
import com.healthmarketscience.jackcess.impl.DatabaseImpl;
import com.healthmarketscience.jackcess.impl.NumberFormatter;
import static com.healthmarketscience.jackcess.impl.expr.FunctionSupport.*;

/**
 *
 * @author James Ahlborn
 */
public class DefaultFunctions
{
  private static final Map<String,Function> FUNCS =
    new HashMap<String,Function>();

  static {
    // load all default functions
    DefaultTextFunctions.init();
    DefaultNumberFunctions.init();
    DefaultDateFunctions.init();
    DefaultFinancialFunctions.init();
  }

  public static final FunctionLookup LOOKUP = new FunctionLookup() {
    public Function getFunction(String name) {
      return FUNCS.get(DatabaseImpl.toLookupName(name));
    }
  };

  private DefaultFunctions() {}


  public static final Function IIF = registerFunc(new Func3("IIf") {
    @Override
    protected Value eval3(EvalContext ctx,
                          Value param1, Value param2, Value param3) {
      // null is false
      return ((!param1.isNull() && param1.getAsBoolean()) ? param2 : param3);
    }
  });

  public static final Function HEX = registerStringFunc(new Func1NullIsNull("Hex") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      if((param1.getType() == Value.Type.STRING) &&
         (param1.getAsString().length() == 0)) {
        return ValueSupport.ZERO_VAL;
      }
      int lv = param1.getAsLongInt();
      return ValueSupport.toValue(Integer.toHexString(lv).toUpperCase());
    }
  });

  public static final Function NZ = registerFunc(new FuncVar("Nz", 1, 2) {
    @Override
    protected Value evalVar(EvalContext ctx, Value[] params) {
      Value param1 = params[0];
      if(!param1.isNull()) {
        return param1;
      }
      if(params.length > 1) {
        return params[1];
      }
      Value.Type resultType = ctx.getResultType();
      return (((resultType == null) ||
               (resultType == Value.Type.STRING)) ?
              ValueSupport.EMPTY_STR_VAL : ValueSupport.ZERO_VAL);
    }
  });

  public static final Function CHOOSE = registerFunc(new FuncVar("Choose", 1, Integer.MAX_VALUE) {
    @Override
    protected Value evalVar(EvalContext ctx, Value[] params) {
      Value param1 = params[0];
      int idx = param1.getAsLongInt();
      if((idx < 1) || (idx >= params.length)) {
        return ValueSupport.NULL_VAL;
      }
      return params[idx];
    }
  });

  public static final Function SWITCH = registerFunc(new FuncVar("Switch") {
    @Override
    protected Value evalVar(EvalContext ctx, Value[] params) {
      if((params.length % 2) != 0) {
        throw new EvalException("Odd number of parameters");
      }
      for(int i = 0; i < params.length; i+=2) {
        if(params[i].getAsBoolean()) {
          return params[i + 1];
        }
      }
      return ValueSupport.NULL_VAL;
    }
  });

  public static final Function OCT = registerStringFunc(new Func1NullIsNull("Oct") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      if((param1.getType() == Value.Type.STRING) &&
         (param1.getAsString().length() == 0)) {
        return ValueSupport.ZERO_VAL;
      }
      int lv = param1.getAsLongInt();
      return ValueSupport.toValue(Integer.toOctalString(lv));
    }
  });

  public static final Function CBOOL = registerFunc(new Func1("CBool") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      boolean b = param1.getAsBoolean();
      return ValueSupport.toValue(b);
    }
  });

  public static final Function CBYTE = registerFunc(new Func1("CByte") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      int lv = param1.getAsLongInt();
      if((lv < 0) || (lv > 255)) {
        throw new EvalException("Byte code '" + lv + "' out of range ");
      }
      return ValueSupport.toValue(lv);
    }
  });

  public static final Function CCUR = registerFunc(new Func1("CCur") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      BigDecimal bd = param1.getAsBigDecimal();
      bd = bd.setScale(4, NumberFormatter.ROUND_MODE);
      return ValueSupport.toValue(bd);
    }
  });

  public static final Function CDATE = registerFunc(new Func1("CDate") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      return DefaultDateFunctions.nonNullToDateValue(ctx, param1);
    }
  });
  static {
    registerFunc("CVDate", CDATE);
  }

  public static final Function CDBL = registerFunc(new Func1("CDbl") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      Double dv = param1.getAsDouble();
      return ValueSupport.toValue(dv);
    }
  });

  public static final Function CDEC = registerFunc(new Func1("CDec") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      BigDecimal bd = param1.getAsBigDecimal();
      return ValueSupport.toValue(bd);
    }
  });

  public static final Function CINT = registerFunc(new Func1("CInt") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      int lv = param1.getAsLongInt();
      if((lv < Short.MIN_VALUE) || (lv > Short.MAX_VALUE)) {
        throw new EvalException("Int value '" + lv + "' out of range ");
      }
      return ValueSupport.toValue(lv);
    }
  });

  public static final Function CLNG = registerFunc(new Func1("CLng") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      int lv = param1.getAsLongInt();
      return ValueSupport.toValue(lv);
    }
  });

  public static final Function CSNG = registerFunc(new Func1("CSng") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      Double dv = param1.getAsDouble();
      if((dv < Float.MIN_VALUE) || (dv > Float.MAX_VALUE)) {
        throw new EvalException("Single value '" + dv + "' out of range ");
      }
      return ValueSupport.toValue(dv.floatValue());
    }
  });

  public static final Function CSTR = registerFunc(new Func1("CStr") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      return ValueSupport.toValue(param1.getAsString());
    }
  });

  public static final Function CVAR = registerFunc(new Func1("CVar") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      return param1;
    }
  });

  public static final Function ISNULL = registerFunc(new Func1("IsNull") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      return ValueSupport.toValue(param1.isNull());
    }
  });

  public static final Function ISDATE = registerFunc(new Func1("IsDate") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      return ValueSupport.toValue(
          !param1.isNull() &&
          (DefaultDateFunctions.nonNullToDateValue(ctx, param1) != null));
    }
  });

  public static final Function ISNUMERIC = registerFunc(new Func1("IsNumeric") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      if(param1.getType().isNumeric()) {
        return ValueSupport.TRUE_VAL;
      }

      if(param1.getType() == Value.Type.STRING) {
        try {
          param1.getAsBigDecimal();
          return ValueSupport.TRUE_VAL;
        } catch(NumberFormatException ignored) {
          // fall through to FALSE_VAL
        }
      }

      return ValueSupport.FALSE_VAL;
    }
  });

  public static final Function VARTYPE = registerFunc(new Func1("VarType") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      Value.Type type = param1.getType();
      int vType = 0;
      switch(type) {
      case NULL:
        // vbNull
        vType = 1;
        break;
      case STRING:
        // vbString
        vType = 8;
        break;
      case DATE:
      case TIME:
      case DATE_TIME:
        // vbDate
        vType = 7;
        break;
      case LONG:
        // vbLong
        vType = 3;
        break;
      case DOUBLE:
        // vbDouble
        vType = 5;
        break;
      case BIG_DEC:
        // vbDecimal
        vType = 14;
        break;
      default:
        throw new EvalException("Unknown type " + type);
      }
      return ValueSupport.toValue(vType);
    }
  });

  public static final Function TYPENAME = registerFunc(new Func1("TypeName") {
    @Override
    protected Value eval1(EvalContext ctx, Value param1) {
      Value.Type type = param1.getType();
      String tName = null;
      switch(type) {
      case NULL:
        tName = "Null";
        break;
      case STRING:
        tName = "String";
        break;
      case DATE:
      case TIME:
      case DATE_TIME:
        tName = "Date";
        break;
      case LONG:
        tName = "Long";
        break;
      case DOUBLE:
        tName = "Double";
        break;
      case BIG_DEC:
        tName = "Decimal";
        break;
      default:
        throw new EvalException("Unknown type " + type);
      }
      return ValueSupport.toValue(tName);
    }
  });



  // https://www.techonthenet.com/access/functions/
  // https://support.office.com/en-us/article/Access-Functions-by-category-b8b136c3-2716-4d39-94a2-658ce330ed83

  static Function registerFunc(Function func) {
    registerFunc(func.getName(), func);
    return func;
  }

  static Function registerStringFunc(Function func) {
    registerFunc(func.getName(), func);
    registerFunc(new StringFuncWrapper(func));
    return func;
  }

  private static void registerFunc(String fname, Function func) {
    String lookupFname = DatabaseImpl.toLookupName(fname);
    if(FUNCS.put(lookupFname, func) != null) {
      throw new IllegalStateException("Duplicate function " + fname);
    }
  }
}
