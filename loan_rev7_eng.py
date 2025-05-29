import streamlit as st
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# 스트림릿 페이지 설정
st.set_page_config(page_title="예비 신혼부부 재정 분석", page_icon="💑")

# session_state에서 값 꺼내기/저장
def get_or_set(key, default):
    if key not in st.session_state:
        st.session_state[key] = default
    return st.session_state[key]

start_year = 2024
husband_last_year = get_or_set("husband_last_year", 2033)
wife_last_year = get_or_set("wife_last_year", 2033)
final_year = max(husband_last_year, wife_last_year, 2033)
end_year = final_year
months_sim = 12 * (end_year - start_year + 1)
year_labels = [f"{y}" for y in range(start_year, end_year+1)]
month_labels = [f"{start_year + i//12}Y {i%12+1}M" for i in range(months_sim)]  # 월 라벨 영어로

def calc_net_salary_from_gross(gross):
    return int(gross * 0.90)

def gross_to_net_list(gross_list):
    return [calc_net_salary_from_gross(g) for g in gross_list]

def monthly_net_from_annual(annual_gross):
    monthly_gross = annual_gross / 12
    net = calc_net_salary_from_gross(monthly_gross)
    return [net] * 12

def apply_raise(base, rate, n):
    return [x * (1+rate/100)**n for x in base]

def get_annual_list(base, rate, start_year, end_year):
    return [apply_raise(base, rate, i) for i in range(end_year-start_year+1)]

months = [f"{i}M" for i in range(1, 13)]  # x축 라벨을 M으로

# 페이지 선택
page = st.sidebar.selectbox(
    "페이지를 선택하세요",
    ["월별 실수령액 시뮬레이션", "집 장만 시뮬레이션", "예상 가계부 시뮬레이션", "통합 자금흐름/잔액 분석"]
)

# ---- 첫번째 페이지 ----
if page == "월별 실수령액 시뮬레이션":
    st.title("💑 Net Monthly Salary + Annual Prediction (with Parental Leave)")
    
    # 1. 남편/아내 출생년도 + 은퇴 나이 입력
    colb1, colb2 = st.columns(2)
    with colb1:
        husband_birth = st.number_input("Husband Year of Birth", min_value=1950, max_value=2020, value=get_or_set("husband_birth", 1990), step=1, key="husband_birth")
        husband_retire_age = st.number_input("Husband Retirement Age", min_value=40, max_value=80, value=get_or_set("husband_retire_age", 60), key="husband_retire_age")
    with colb2:
        wife_birth = st.number_input("Wife Year of Birth", min_value=1950, max_value=2020, value=get_or_set("wife_birth", 1992), step=1, key="wife_birth")
        wife_retire_age = st.number_input("Wife Retirement Age", min_value=40, max_value=80, value=get_or_set("wife_retire_age", 60), key="wife_retire_age")
    husband_last_year = husband_birth + husband_retire_age - 1
    wife_last_year = wife_birth + wife_retire_age - 1
    final_year = max(husband_last_year, wife_last_year)
    current_year = 2024
    years = [y for y in range(current_year, final_year+1)]
    year_labels = [f"{y}" for y in years]
    st.session_state["husband_last_year"] = husband_last_year
    st.session_state["wife_last_year"] = wife_last_year

    # --- 소득입력 ---
    st.markdown("#### 💙 Husband Income Input")
    husband_mode = st.selectbox(
        "Input Method (Husband)", 
        ["Annual Gross Salary", "Monthly Gross Salary", "Monthly Net Salary"], 
        key="husband_mode"
    )
    if husband_mode == "Annual Gross Salary":
        husband_annual = st.number_input("Husband Annual Gross (10,000 KRW)", min_value=0, value=get_or_set("husband_annual", 4800), step=100, key="husband_annual")
        husband_net = monthly_net_from_annual(husband_annual)
        husband_gross_base = [husband_annual / 12] * 12
    elif husband_mode == "Monthly Gross Salary":
        husband_gross = [st.number_input(f"Husband {m} Gross (10,000 KRW)", min_value=0, value=get_or_set(f"h_g_{i}", 400), step=10, key=f"h_g_{i}") for i, m in enumerate(months)]
        husband_net = gross_to_net_list(husband_gross)
        husband_gross_base = husband_gross
    else:
        husband_net = [st.number_input(f"Husband {m} Net (10,000 KRW)", min_value=0, value=get_or_set(f"h_n_{i}", 360), step=10, key=f"h_n_{i}") for i, m in enumerate(months)]
        husband_gross_base = [val/0.9 for val in husband_net]
    
    st.markdown("#### 💖 Wife Income Input")
    wife_mode = st.selectbox(
        "Input Method (Wife)", 
        ["Annual Gross Salary", "Monthly Gross Salary", "Monthly Net Salary"], 
        key="wife_mode"
    )
    if wife_mode == "Annual Gross Salary":
        wife_annual = st.number_input("Wife Annual Gross (10,000 KRW)", min_value=0, value=get_or_set("wife_annual", 3600), step=100, key="wife_annual")
        wife_net = monthly_net_from_annual(wife_annual)
        wife_gross_base = [wife_annual / 12] * 12
    elif wife_mode == "Monthly Gross Salary":
        wife_gross = [st.number_input(f"Wife {m} Gross (10,000 KRW)", min_value=0, value=get_or_set(f"w_g_{i}", 300), step=10, key=f"w_g_{i}") for i, m in enumerate(months)]
        wife_net = gross_to_net_list(wife_gross)
        wife_gross_base = wife_gross
    else:
        wife_net = [st.number_input(f"Wife {m} Net (10,000 KRW)", min_value=0, value=get_or_set(f"w_n_{i}", 270), step=10, key=f"w_n_{i}") for i, m in enumerate(months)]
        wife_gross_base = [val/0.9 for val in wife_net]
    
    st.markdown("---")
    st.markdown("### 📈 Annual Raise Rate")
    st.caption("💡 *Korea 10-year avg. salary increase (2014~2023): ~3.5%*")
    col1, col2 = st.columns(2)
    with col1:
        husband_rate = st.slider("Husband Annual Raise (%)", min_value=1.0, max_value=10.0, step=0.5, value=get_or_set("husband_rate", 3.5), key="husband_rate")
    with col2:
        wife_rate = st.slider("Wife Annual Raise (%)", min_value=1.0, max_value=10.0, step=0.5, value=get_or_set("wife_rate", 3.5), key="wife_rate")
    
    st.markdown("---")
    st.markdown("### 👶 Parental Leave")
    st.markdown("#### Husband Parental Leave")
    colh1, colh2, colh3 = st.columns(3)
    with colh1:
        saved_value = st.session_state.get("hy_start_year", years[0])
        idx = years.index(saved_value) if saved_value in years else 0
        hy_start_year = st.selectbox("Husband Leave Start Year", years, index=idx, key="hy_start_year")
        # 이 아래 줄 완전히 삭제!
        # st.session_state["hy_start_year"] = hy_start_year


    with colh2:
        saved_month = st.session_state.get("hy_start_month", 1)
        idx = list(range(1, 13)).index(saved_month) if saved_month in range(1, 13) else 0
        hy_start_month = st.selectbox("Husband Leave Start Month", list(range(1, 13)), index=idx, key="hy_start_month")
        #st.session_state["hy_start_month"] = hy_start_month

    with colh3:
        hy_months = st.number_input("Husband Leave Months", min_value=0, max_value=36, value=get_or_set("hy_months", 0), key="hy_months")
    st.markdown("#### Wife Parental Leave")
    colw1, colw2, colw3 = st.columns(3)
    with colw1:
        saved_value = st.session_state.get("wy_start_year", years[0])
        idx = years.index(saved_value) if saved_value in years else 0
        wy_start_year = st.selectbox("Wife Leave Start Year", years, index=idx, key="wy_start_year")
        # st.session_state["wy_start_year"] = wy_start_year  ← 이 줄 삭제!


    with colw2:
        saved_month = st.session_state.get("wy_start_month", 1)
        idx = list(range(1, 13)).index(saved_month) if saved_month in range(1, 13) else 0
        wy_start_month = st.selectbox("Wife Leave Start Month", list(range(1, 13)), index=idx, key="wy_start_month")
        #st.session_state["wy_start_month"] = wy_start_month

    with colw3:
        wy_months = st.number_input("Wife Leave Months", min_value=0, max_value=36, value=get_or_set("wy_months", 0), key="wy_months")
    
    st.markdown("#### Parental Leave Pay Mode")
    leave_pay_mode = st.selectbox(
        "Select Parental Leave Pay Calculation",
        ["Auto Calculation", "Manual Input (1~12M)"],
        key="leave_pay_mode"
    )
    custom_leave_pay = None
    if leave_pay_mode == "Manual Input (1~12M)":
        custom_leave_pay = []
        st.markdown("##### Parental Leave Pay 1~12M (10,000 KRW)")
        cols = st.columns(12)
        for i in range(12):
            amt = st.number_input(f"{i+1}M", min_value=0, value=get_or_set(f"custom_leave_{i}", 150), step=1, key=f"custom_leave_{i}")
            custom_leave_pay.append(amt)
    
    def get_parental_leave_pay(gross, idx):
        if leave_pay_mode == "Manual Input (1~12M)" and custom_leave_pay is not None and idx < len(custom_leave_pay):
            return custom_leave_pay[idx]
        # Auto calc (government rule)
        if idx < 3:  # 1~3M
            pay = gross * 1.0
            limit = 250
        elif idx < 6:  # 4~6M
            pay = gross * 1.0
            limit = 200
        else:
            pay = gross * 0.8
            limit = 150
        result = int(pay * 0.9)
        return min(result, limit)
    
    checked = st.multiselect("Select year(s) to check", year_labels, default=[year_labels[0]], key="net_salary_years_checked")
    def yearly_net(base, rate, years):
        return [gross_to_net_list(apply_raise(base, rate, i)) for i in range(len(years))]
    
    husband_years_net = yearly_net(husband_gross_base, husband_rate, years)
    wife_years_net = yearly_net(wife_gross_base, wife_rate, years)
    st.session_state['husband_years_net'] = husband_years_net
    st.session_state['wife_years_net'] = wife_years_net

    def insert_parental_leave(yearly_income, years, leave_start_year, leave_start_month, leave_months, base_gross, rate):
        if leave_months == 0:
            return yearly_income
        try:
            y_idx = years.index(leave_start_year)
        except ValueError:
            return yearly_income
        m_idx = leave_start_month - 1
        for i in range(leave_months):
            cy = y_idx + (m_idx + i)//12
            cm = (m_idx + i)%12
            if cy < len(yearly_income):
                gross = apply_raise(base_gross, rate, cy)[cm]
                leave_pay = get_parental_leave_pay(gross, i)
                yearly_income[cy][cm] = leave_pay
        return yearly_income
    
    husband_years_net = insert_parental_leave(
        husband_years_net, years, hy_start_year, hy_start_month, hy_months, husband_gross_base, husband_rate
    )
    wife_years_net = insert_parental_leave(
        wife_years_net, years, wy_start_year, wy_start_month, wy_months, wife_gross_base, wife_rate
    )
    st.session_state['husband_years_net'] = husband_years_net
    st.session_state['wife_years_net'] = wife_years_net

    if checked:
        for idx, label in enumerate(year_labels):
            if label in checked:
                df = pd.DataFrame({
                    "M": months,
                    "Husband Net": husband_years_net[idx],
                    "Wife Net": wife_years_net[idx],
                    "Total": np.array(husband_years_net[idx]) + np.array(wife_years_net[idx])
                })
                st.markdown(f"#### {label}Y Monthly Net Income")
                st.dataframe(df)
                fig, ax = plt.subplots(figsize=(8, 5))
                ax.bar(df["M"], df["Husband Net"], label="Husband", color="#5B9BD5")
                ax.bar(df["M"], df["Wife Net"], label="Wife", bottom=df["Husband Net"], color="#ED7D31")
                for i, total in enumerate(df["Total"]):
                    ax.text(i, total + 2, f"{int(total):,} ", ha='center', va='bottom', fontsize=6, fontweight='bold')
                ax.set_ylabel("Monthly Net Salary ")
                ax.set_xlabel("M")
                ax.set_title(f"{label}Y Couple Monthly Net Income (Stacked, Parental Leave Applied)")
                ax.legend()
                st.pyplot(fig)

# ---- 두번째 페이지 ----
elif page == "집 장만 시뮬레이션":
    st.title("🏠 Home Purchase vs Jeonse Simulation")
    house_mode = st.radio("Housing Option", ["Purchase", "Jeonse"], key="house_mode")
    if house_mode == "Purchase":
        st.subheader("🏡 Purchase Conditions")
        cash = st.number_input("Cash ", min_value=0, value=get_or_set("house_cash", 30000), step=100, key="house_cash")
        house_price = st.number_input("Purchase Price ", min_value=0, value=get_or_set("house_price", 70000), step=500, key="house_price")
        need_loan = max(house_price - cash, 0)
        st.write(f"💡 Loan Needed: **{need_loan:,} **")
        loan_year = st.slider("Loan Term (Y)", min_value=10, max_value=40, value=get_or_set("loan_year", 30), key="loan_year")
        loan_rate = st.slider("Interest Rate (%)", min_value=2.0, max_value=8.0, value=get_or_set("loan_rate", 3.8), step=0.1, key="loan_rate")
        st.caption("※ 2024 typical rate: 3.5~4.5%")
        if house_price > 0:
            leverage = need_loan / house_price
            st.info(f"💸 **Leverage: {leverage*100:.1f}%** (Loan/Total)")
        st.markdown("#### 📈 House Price Change Scenario")
        up_rate = st.slider("House Price Annual Up (%)", min_value=-5.0, max_value=10.0, value=get_or_set("house_up_rate", 3.0), step=0.1, key="house_up_rate")
        dn_rate = st.slider("House Price Annual Down (%)", min_value=-10.0, max_value=0.0, value=get_or_set("house_dn_rate", -2.0), step=0.1, key="house_dn_rate")
        period = st.slider("Simulation Years", min_value=1, max_value=30, value=get_or_set("house_period", 10), key="house_period")
        r = loan_rate / 100 / 12
        n = loan_year * 12
        if r > 0 and need_loan > 0:
            monthly_payment = need_loan * 10000 * r * (1 + r) ** n / ((1 + r) ** n - 1)
            monthly_payment = int(monthly_payment // 10) * 10
        else:
            monthly_payment = 0
        st.write(f"📅 Monthly Loan Repayment: **{monthly_payment/10000:,.1f} **")
        # 이 부분 추가!
        st.session_state['last_housing_payment'] = int(monthly_payment/10000)

        years = np.arange(1, period+1)
        house_up = [house_price * ((1 + up_rate/100) ** i) for i in years]
        house_dn = [house_price * ((1 + dn_rate/100) ** i) for i in years]
        loan_balance = []
        cur_balance = need_loan * 10000
        for i in years:
            if r > 0:
                remain = cur_balance * ((1 + r) ** (n) - (1 + r) ** (i * 12)) / ((1 + r) ** (n) - 1)
            else:
                remain = max(cur_balance - monthly_payment * 12 * i, 0)
            loan_balance.append(remain / 10000)
        equity_up = [up - loan_balance[i] for i, up in enumerate(house_up)]
        equity_dn = [dn - loan_balance[i] for i, dn in enumerate(house_dn)]
        df = pd.DataFrame({
            "Y": years,
            "House (Up)": house_up,
            "House (Down)": house_dn,
            "Loan Left": loan_balance,
            "Net Assets (Up)": equity_up,
            "Net Assets (Down)": equity_dn
        })
        st.dataframe(df.style.format({
            "House (Up)": "{:,.0f}",
            "House (Down)": "{:,.0f}",
            "Loan Left": "{:,.0f}",
            "Net Assets (Up)": "{:,.0f}",
            "Net Assets (Down)": "{:,.0f}",
        }))
        fig, ax = plt.subplots(figsize=(7, 4))
        ax.plot(df["Y"], df["Net Assets (Up)"], marker='o', label="Net Assets (Up)")
        ax.plot(df["Y"], df["Net Assets (Down)"], marker='o', label="Net Assets (Down)")
        ax.set_xlabel("Y")
        ax.set_ylabel("Net Assets ")
        ax.set_title("Net Assets Scenario (Buy House)")
        ax.legend()
        st.pyplot(fig)
        st.caption("""
        **Note:**  
        - Taxes, fees, living cost, rent, actual salary/saving not included.
        - Rates/scenario for reference only.
        """)
    elif house_mode == "Jeonse":
        st.subheader("🏡 Jeonse Conditions")
        deposit = st.number_input("Deposit ", min_value=0, value=get_or_set("jeonse_deposit", 40000), step=500, key="jeonse_deposit")
        cash = st.number_input("Cash ", min_value=0, value=get_or_set("jeonse_cash", 30000), step=100, key="jeonse_cash")
        st.info(f"Deposit {deposit:,} x10k, Cash {cash:,} x10k")
        st.markdown("""
        #### ⚖️ Jeonse = Deposit + Cash
        - No risk of loss/leverage/price change.
        """)
    	st.session_state['last_housing_payment'] = 0
# ---- 세번째 페이지 ----
elif page == "예상 가계부 시뮬레이션":
    st.title("📝 Expected Budget Simulation")
    husband_last_year = st.session_state.get("husband_last_year", 2033)
    wife_last_year = st.session_state.get("wife_last_year", 2033)
    final_year = max(husband_last_year, wife_last_year, 2033)
    start_year = 2024
    end_year = final_year
    months_sim = 12 * (end_year - start_year + 1)
    year_labels = [f"{y}" for y in range(start_year, end_year+1)]
    month_labels = [f"{start_year + i//12}Y {i%12+1}M" for i in range(months_sim)]

    st.markdown("#### 1. Inflation Rate")
    inflation = st.slider("Annual Inflation (%)", min_value=0.0, max_value=10.0, step=0.1, value=get_or_set("inflation", 2.2), key="inflation")
    st.markdown("---")
    st.markdown("#### 2. Child Plan")
    num_children = st.selectbox("Expected Number of Children (max 3)", [0, 1, 2, 3], index=get_or_set("num_children", 1), key="num_children")
    child_plan = []
    for i in range(num_children):
        st.markdown(f"##### Child {i+1} Birth")
        colc1, colc2 = st.columns(2)
        with colc1:
            y = st.number_input(
                f"Child {i+1} Birth Year",
                min_value=2024, max_value=2050,
                value=get_or_set(f"childy_{i}", 2025),
                key=f"childy_{i}"
            )
        with colc2:
            # 아래 부분만 수정
            saved_month = st.session_state.get(f"childm_{i}", 1)  # 1~12월로 저장됨
            idx = saved_month - 1 if saved_month in range(1, 13) else 0
            m = st.selectbox(
                f"Child {i+1} Birth Month",
                list(range(1, 13)),
                index=idx,
                key=f"childm_{i}"
            )
        child_plan.append((y, m))
    # ... 이하 get_childcare_cost 등 함수 및 예시 생략 (기존 코드 복사)
    st.caption("""
    ※ 육아비(자녀 1인 기준):  
    - 보건복지부 등 공공 통계에 따르면 2024년 신생아~20세까지 약 **3억원(월평균 120만원)**이 소요됨  
    - (의식주, 교육비 포함, 사교육/특별 이벤트 제외 평균치, 실제는 가정별 차이)
    """)

    # 육아비 공식: 월별로 20년간 120만원(연마다 물가상승률 반영)
    def get_childcare_cost(start_year, start_month, months, inflation=2.2):
        """출생연도·월부터 22세까지, 연령별로 차등. start_month는 1~12"""
        costs = np.zeros(months)
        for i in range(months):
            cur_year = 2024 + (i // 12)
            cur_month = (i % 12) + 1
            # 자녀의 만 나이 (개월)
            age_month = (cur_year - start_year) * 12 + (cur_month - start_month)
            if age_month < 0 or age_month >= 12 * 23:  # 만 0세~22세까지만 반영
                continue
            # 나이구간별 기준
            if age_month < 36:       # 0~2세
                base = 60
            elif age_month < 84:     # 3~6세
                base = 80
            elif age_month < 156:    # 7~12세
                base = 100
            elif age_month < 228:    # 13~18세
                base = 140
            elif age_month < 276:    # 19~22세(대학생)
                base = 180
            else:
                base = 0
            # 인플레이션 반영(기준은 '현재년도-2024')
            year_offset = cur_year - 2024
            costs[i] = base * ((1 + inflation / 100) ** year_offset)
        return costs

    st.markdown("---")
    st.markdown("#### 3. 고정비/변동비(수정·추가 가능)")
    # 기본 예시
    default_fixed = {
        "관리비": 20,
        "통신비": 10,
        "보험료": 15,
        "자동차유지비": 20,
        "식비": 60
    }
    default_var = {
        "의류잡화": 10,
        "외식/문화": 15,
        "교통비": 8
    }
    st.markdown("##### 고정비")
    if "fixed_expenses" not in st.session_state:
        st.session_state.fixed_expenses = default_fixed.copy()
    for key in list(st.session_state.fixed_expenses):
        st.session_state.fixed_expenses[key] = st.number_input(f"{key} (만원/월)", min_value=0, value=st.session_state.fixed_expenses[key], step=1)
    new_fixed = st.text_input("고정비 항목 추가(이름 입력 후 Enter)", "")
    if new_fixed:
        st.session_state.fixed_expenses[new_fixed] = 10

    st.markdown("##### 변동비")
    if "var_expenses" not in st.session_state:
        st.session_state.var_expenses = default_var.copy()
    for key in list(st.session_state.var_expenses):
        st.session_state.var_expenses[key] = st.number_input(f"{key} (만원/월)", min_value=0, value=st.session_state.var_expenses[key], step=1)
    new_var = st.text_input("변동비 항목 추가(이름 입력 후 Enter)", "", key="var_add")
    if new_var:
        st.session_state.var_expenses[new_var] = 5

    # ---- 부동산비용 자동입력
    st.markdown("##### 주거비(자동 입력)")
    if "last_housing_payment" in st.session_state:
        housing_payment = st.session_state.last_housing_payment
    else:
        housing_payment = st.number_input("월 주거비(대출상환/전세/월세)", min_value=0, value=130, step=1)
    st.session_state.last_housing_payment = housing_payment

    # 각 항목 월별 배열
    fixed_arr = np.zeros(months_sim)
    var_arr = np.zeros(months_sim)
    childcare_arr = np.zeros(months_sim)
    housing_arr = np.zeros(months_sim)

    # 고정비
    for v in st.session_state.fixed_expenses.values():
        for i in range(months_sim):
            year_offset = i // 12
            fixed_arr[i] += v * ((1 + inflation / 100) ** year_offset)
    # 변동비
    for v in st.session_state.var_expenses.values():
        for i in range(months_sim):
            year_offset = i // 12
            var_arr[i] += v * ((1 + inflation / 100) ** year_offset)
    # 주거비
    for i in range(months_sim):
        year_offset = i // 12
        housing_arr[i] += housing_payment * ((1 + inflation / 100) ** year_offset)
    # 육아비
    for (cy, cm) in child_plan:
        # cy=출생연도, cm=출생월(1~12)
        arr = get_childcare_cost(cy, cm, months_sim, inflation=inflation)
        childcare_arr += arr

    total_arr = fixed_arr + var_arr + childcare_arr + housing_arr

    # DataFrame
    df = pd.DataFrame({
        "월": month_labels,
        "고정비합": fixed_arr,
        "변동비합": var_arr,
        "육아비합": childcare_arr,
        "주거비합": housing_arr,
        "합계": total_arr
    })

    st.session_state['expense_df'] = df

    # ---- 연도별 선택 ----

    # 아래는 그래프 부분만 예시
    color_map = ["#5B9BD5", "#ED7D31", "#A9D18E", "#FFD966"]
    checked = st.multiselect("Select year(s) to check", year_labels, default=[year_labels[0]], key="expense_years_checked")

    # ... fixed/var/housing/childcare cost 계산 및 DataFrame 생성 (생략)

    for year_label in checked:
        year = int(year_label)
        st.markdown(f"#### {year}Y Monthly Expenses")
        idx_start = (year - start_year) * 12
        idx_end = idx_start + 12
        df_year = df.iloc[idx_start:idx_end].copy()
        df_year.reset_index(drop=True, inplace=True)

        fig, ax = plt.subplots(figsize=(8, 5))
        x = np.arange(12)
        b1 = ax.bar(x, df_year["고정비합"], label="Fixed Cost", color=color_map[0])
        b2 = ax.bar(x, df_year["변동비합"], bottom=df_year["고정비합"], label="Variable Cost", color=color_map[1])
        b3 = ax.bar(x, df_year["육아비합"], bottom=df_year["고정비합"]+df_year["변동비합"], label="Childcare", color=color_map[2])
        b4 = ax.bar(x, df_year["주거비합"], bottom=df_year["고정비합"]+df_year["변동비합"]+df_year["육아비합"], label="Housing", color=color_map[3])
        for i, total in enumerate(df_year["합계"]):
            ax.text(i, total + 2, f"{int(total):,} ", ha='center', va='bottom', fontsize=6, fontweight='bold')
        ax.set_xticks(x)
        ax.set_xticklabels([f"M{i+1}" for i in range(12)])  # x라벨 M으로!
        ax.set_ylabel("Monthly Expenses ")
        ax.set_xlabel("M")
        ax.set_title(f"{year}Y Monthly Expenses (Stacked, Inflation Applied)")
        ax.legend()
        st.pyplot(fig)
        st.dataframe(df_year)

# ---- 네번째 페이지 ----
elif page == "통합 자금흐름/잔액 분석":
    st.title("💰 Integrated Cash Flow/Balance Analysis")
    husband_years_net = st.session_state.get('husband_years_net')
    wife_years_net = st.session_state.get('wife_years_net')
    df = st.session_state.get('expense_df')

    if husband_years_net is None or wife_years_net is None or df is None:
        st.error("❗️First enter/generate data on Page 1 (Net Salary) and Page 3 (Budget)!")
        st.stop()

    view_mode = st.radio("View by", ["Yearly", "Monthly"], horizontal=True, key="view_mode")
    options = st.multiselect("Select items", ["Income", "Expense", "Net (Savable)"], default=["Income", "Expense", "Net (Savable)"], key="cf_options")
    all_years = [int(y) for y in year_labels]
    income_monthly = []
    for i, y in enumerate(all_years):
        income_monthly.extend(np.array(husband_years_net[i]) + np.array(wife_years_net[i]))
    expense_monthly = df["합계"].tolist()
    net_monthly = np.array(income_monthly) - np.array(expense_monthly)

    if view_mode == "Yearly":
        years = [int(y) for y in year_labels]
        n_years = len(years)
        income_annual = [sum(income_monthly[i*12:(i+1)*12]) for i in range(n_years)]
        expense_annual = [sum(expense_monthly[i*12:(i+1)*12]) for i in range(n_years)]
        net_annual = [sum(net_monthly[i*12:(i+1)*12]) for i in range(n_years)]
        width = 0.25
        x = np.arange(n_years)
        fig, ax = plt.subplots(figsize=(10, 6))
        offset = 0
        if "Income" in options:
            ax.bar(x + offset, income_annual, width, label="Income", color="#5B9BD5")
            offset += width
        if "Expense" in options:
            ax.bar(x + offset, expense_annual, width, label="Expense", color="#ED7D31")
            offset += width
        if "Net (Savable)" in options:
            ax.bar(x + offset, net_annual, width, label="Net (Savable)", color="#A9D18E")
        ax.set_xticks(x + width)
        ax.set_xticklabels(year_labels, fontsize=10, rotation=45)
        ax.set_ylabel("Annual Amount ")
        ax.set_xlabel("Y")
        ax.set_title("Annual Income / Expense / Net Savings")
        ax.legend()
        st.pyplot(fig)
        summary_df = pd.DataFrame({
            "Y": year_labels,
            "Income": income_annual,
            "Expense": expense_annual,
            "Net (Savable)": net_annual
        })
        st.dataframe(summary_df)
    else:
        min_year = all_years[0]
        max_year = all_years[-1]
        sel_start_year = st.selectbox("Start Year", all_years, index=0, key="cf_sel_start_year")
        sel_period = st.slider("How many years?", min_value=1, max_value=min(5, max_year-sel_start_year+1), value=1, key="cf_sel_period")
        start_idx = (sel_start_year - min_year) * 12
        end_idx = start_idx + sel_period*12
        sel_month_labels = [f"{min_year + (i//12)}Y {i%12+1}M" for i in range(start_idx, end_idx)]
        sel_income_monthly = income_monthly[start_idx:end_idx]
        sel_expense_monthly = expense_monthly[start_idx:end_idx]
        sel_net_monthly = net_monthly[start_idx:end_idx]
        fig, ax = plt.subplots(figsize=(max(10, sel_period*5), 5))
        x = np.arange(len(sel_month_labels))
        width = 0.25
        offset = 0
        label_shown = False
        if "Income" in options:
            ax.bar(x + offset, sel_income_monthly, width, label="Income", color="#5B9BD5")
            offset += width
            label_shown = True
        if "Expense" in options:
            ax.bar(x + offset, sel_expense_monthly, width, label="Expense", color="#ED7D31")
            offset += width
            label_shown = True
        if "Net (Savable)" in options:
            ax.bar(x + offset, sel_net_monthly, width, label="Net (Savable)", color="#A9D18E")
            label_shown = True
        ax.set_xticks(x + width)
        step = max(1, len(sel_month_labels)//20)
        ax.set_xticklabels([sel_month_labels[i] if i%step==0 else "" for i in range(len(sel_month_labels))], rotation=45, fontsize=8)
        ax.set_ylabel("Monthly Amount ")
        ax.set_xlabel("Y, M")
        ax.set_title(f"{sel_start_year}Y~{sel_start_year+sel_period-1}Y Monthly Income / Expense / Net Savings")
        if label_shown:
            ax.legend()
        st.pyplot(fig)

        st.markdown("#### 💹 Cumulative Net Cash Flow")
        cumulative = np.cumsum(sel_net_monthly)
        fig2, ax2 = plt.subplots(figsize=(max(10, sel_period*5), 3))
        ax2.plot(x, cumulative, color="#7030A0", marker="o")
        ax2.axhline(0, color="gray", linestyle="--", linewidth=1)
        ax2.set_xticks(x)
        ax2.set_xticklabels([sel_month_labels[i] if i%step==0 else "" for i in range(len(sel_month_labels))], rotation=45, fontsize=8)
        ax2.set_ylabel("Cumulative Cash Flow ")
        ax2.set_xlabel("Y, M")
        ax2.set_title("Cumulative Net Cash Flow (Monthly Net Savings)")
        st.pyplot(fig2)

        month_df = pd.DataFrame({
            "M": sel_month_labels,
            "Income": sel_income_monthly,
            "Expense": sel_expense_monthly,
            "Net (Savable)": sel_net_monthly,
            "Cumulative": cumulative
        })
        st.dataframe(month_df)
