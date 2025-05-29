import streamlit as st
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

plt.rcParams['font.family'] = 'Malgun Gothic'
plt.rcParams['axes.unicode_minus'] = False

st.set_page_config(page_title="예비 신혼부부 재정 분석", page_icon="💑")

start_year = 2024
husband_last_year = st.session_state.get("husband_last_year", 2033)
wife_last_year = st.session_state.get("wife_last_year", 2033)
final_year = max(husband_last_year, wife_last_year, 2033)
end_year = final_year
months_sim = 12 * (end_year - start_year + 1)
year_labels = [f"{y}년" for y in range(start_year, end_year+1)]
month_labels = [f"{start_year + i//12}년 {i%12+1}월" for i in range(months_sim)]

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

months = [f"{i}월" for i in range(1, 13)]


# 페이지 선택
page = st.sidebar.selectbox(
    "페이지를 선택하세요",
    ["월별 실수령액 시뮬레이션", "집 장만 시뮬레이션", "예상 가계부 시뮬레이션", "통합 자금흐름/잔액 분석"]
)

# ---- 첫번째 페이지 ----
if page == "월별 실수령액 시뮬레이션":
    
    st.title("💑 남편·아내 월별 실수령액 + 연도별 예측 (육아휴직 포함)")
    
    # 1. 남편/아내 출생년도 + 은퇴 나이 입력
    colb1, colb2 = st.columns(2)
    with colb1:
        husband_birth = st.number_input("남편 출생년도", min_value=1950, max_value=2020, value=1990, step=1)
        husband_retire_age = st.number_input("남편 몇 살까지 일할 예정?", min_value=40, max_value=80, value=60)
    with colb2:
        wife_birth = st.number_input("아내 출생년도", min_value=1950, max_value=2020, value=1992, step=1)
        wife_retire_age = st.number_input("아내 몇 살까지 일할 예정?", min_value=40, max_value=80, value=60)
    
    husband_last_year = husband_birth + husband_retire_age - 1
    wife_last_year = wife_birth + wife_retire_age - 1
    final_year = max(husband_last_year, wife_last_year)
    current_year = 2024
    years = [y for y in range(current_year, final_year+1)]
    year_labels = [f"{y}년" for y in years]
    st.session_state["husband_last_year"] = husband_last_year
    st.session_state["wife_last_year"] = wife_last_year

    ### --- 소득입력 ---
    st.markdown("#### 💙 남편 소득 입력 방식 선택")
    husband_mode = st.selectbox(
        "남편의 입력 방식", 
        ["연간 원천징수(총급여) 입력", "월별 세전급여 입력", "월별 실수령액 입력"], 
        key="husband_mode"
    )
    if husband_mode == "연간 원천징수(총급여) 입력":
        husband_annual = st.number_input("남편 연간 총급여(만원)", min_value=0, value=4800, step=100)
        husband_net = monthly_net_from_annual(husband_annual)
        husband_gross_base = [husband_annual / 12] * 12
    elif husband_mode == "월별 세전급여 입력":
        husband_gross = [st.number_input(f"남편 {m} 세전급여(만원)", min_value=0, value=400, step=10, key=f"h_g_{i}") for i, m in enumerate(months)]
        husband_net = gross_to_net_list(husband_gross)
        husband_gross_base = husband_gross
    else:
        husband_net = [st.number_input(f"남편 {m} 실수령액(만원)", min_value=0, value=360, step=10, key=f"h_n_{i}") for i, m in enumerate(months)]
        husband_gross_base = [val/0.9 for val in husband_net] # 근사역산
    
    st.markdown("#### 💖 아내 소득 입력 방식 선택")
    wife_mode = st.selectbox(
        "아내의 입력 방식", 
        ["연간 원천징수(총급여) 입력", "월별 세전급여 입력", "월별 실수령액 입력"], 
        key="wife_mode"
    )
    if wife_mode == "연간 원천징수(총급여) 입력":
        wife_annual = st.number_input("아내 연간 총급여(만원)", min_value=0, value=3600, step=100)
        wife_net = monthly_net_from_annual(wife_annual)
        wife_gross_base = [wife_annual / 12] * 12
    elif wife_mode == "월별 세전급여 입력":
        wife_gross = [st.number_input(f"아내 {m} 세전급여(만원)", min_value=0, value=300, step=10, key=f"w_g_{i}") for i, m in enumerate(months)]
        wife_net = gross_to_net_list(wife_gross)
        wife_gross_base = wife_gross
    else:
        wife_net = [st.number_input(f"아내 {m} 실수령액(만원)", min_value=0, value=270, step=10, key=f"w_n_{i}") for i, m in enumerate(months)]
        wife_gross_base = [val/0.9 for val in wife_net]
    
    st.markdown("---")
    st.markdown("### 📈 연봉상승률 입력")
    st.caption("💡 *한국 10년 평균 연봉상승률(2014~2023): 약 3.5% (국내 자료 기준)*")
    col1, col2 = st.columns(2)
    with col1:
        husband_rate = st.slider("남편 연봉상승률(%)", min_value=1.0, max_value=10.0, step=0.5, value=3.5)
    with col2:
        wife_rate = st.slider("아내 연봉상승률(%)", min_value=1.0, max_value=10.0, step=0.5, value=3.5)
    
    ### --- 육아휴직 입력 ---
    st.markdown("---")
    st.markdown("### 👶 육아휴직 입력")
    
    st.markdown("#### 남편 육아휴직(없으면 0으로)")
    colh1, colh2, colh3 = st.columns(3)
    with colh1:
        hy_start_year = st.selectbox("남편 시작연도", years, index=0, key="hy_start_year")
    with colh2:
        hy_start_month = st.selectbox("남편 시작월", list(range(1,13)), index=0, key="hy_start_month")
    with colh3:
        hy_months = st.number_input("남편 육아휴직 개월수", min_value=0, max_value=36, value=0, key="hy_months")
    
    st.markdown("#### 아내 육아휴직(없으면 0으로)")
    colw1, colw2, colw3 = st.columns(3)
    with colw1:
        wy_start_year = st.selectbox("아내 시작연도", years, index=0, key="wy_start_year")
    with colw2:
        wy_start_month = st.selectbox("아내 시작월", list(range(1,13)), index=0, key="wy_start_month")
    with colw3:
        wy_months = st.number_input("아내 육아휴직 개월수", min_value=0, max_value=36, value=0, key="wy_months")
    
    # 육아휴직 급여 입력 방식 선택
    st.markdown("#### 육아휴직 급여 입력 방식")
    leave_pay_mode = st.selectbox(
        "육아휴직 급여 산정 방식 선택",
        ["자동 계산(정부 규정)", "수동 입력(1~12개월차 직접 입력)"],
        key="leave_pay_mode"
    )
    
    # 수동 입력창
    custom_leave_pay = None
    if leave_pay_mode == "수동 입력(1~12개월차 직접 입력)":
        custom_leave_pay = []
        st.markdown("##### [남편/아내 공통] 육아휴직 1~12개월차 급여 직접 입력 (만원)")
        cols = st.columns(12)
        for i in range(12):
            with cols[i]:
                amt = st.number_input(f"{i+1}개월차", min_value=0, value=150, step=1, key=f"custom_leave_{i}")
                custom_leave_pay.append(amt)
    
    # 수정된 get_parental_leave_pay 함수
    def get_parental_leave_pay(gross, idx):
        if leave_pay_mode == "수동 입력(1~12개월차 직접 입력)" and custom_leave_pay is not None and idx < len(custom_leave_pay):
            return custom_leave_pay[idx]
        # 자동계산(정부 규정)
        if idx < 3:  # 1~3개월
            pay = gross * 1.0
            limit = 250
        elif idx < 6:  # 4~6개월
            pay = gross * 1.0
            limit = 200
        else:  # 7개월~
            pay = gross * 0.8
            limit = 150
        result = int(pay * 0.9)  # 실수령 90%
        return min(result, limit)
    
    ### --- 연도 선택 ---
    checked = st.multiselect("확인하고 싶은 연도를 모두 선택하세요.", year_labels, default=[year_labels[0]])
    
    ### --- 연도별 월별 실수령액 계산 (연봉상승률 적용) ---
    def yearly_net(base, rate, years):
        return [gross_to_net_list(apply_raise(base, rate, i)) for i in range(len(years))]
    
    husband_years_net = yearly_net(husband_gross_base, husband_rate, years)
    wife_years_net = yearly_net(wife_gross_base, wife_rate, years)
    st.session_state['husband_years_net'] = husband_years_net
    st.session_state['wife_years_net'] = wife_years_net

    def insert_parental_leave(yearly_income, years, leave_start_year, leave_start_month, leave_months, base_gross, rate):
        # yearly_income: [연도][월] (실수령 리스트)
        if leave_months == 0:
            return yearly_income  # 육아휴직 없으면 그대로 리턴
        try:
            y_idx = years.index(leave_start_year)
        except ValueError:
            return yearly_income
        m_idx = leave_start_month - 1  # 0~11 (월-1)
        for i in range(leave_months):
            cy = y_idx + (m_idx + i)//12
            cm = (m_idx + i)%12
            if cy < len(yearly_income):
                # 해당 연도, 해당 월의 gross (연봉상승률 반영)
                gross = apply_raise(base_gross, rate, cy)[cm]
                leave_pay = get_parental_leave_pay(gross, i)
                yearly_income[cy][cm] = leave_pay
        return yearly_income
    
    # 남편/아내 각각 육아휴직 반영
    husband_years_net = insert_parental_leave(
        husband_years_net, years, hy_start_year, hy_start_month, hy_months, husband_gross_base, husband_rate
    )
    wife_years_net = insert_parental_leave(
        wife_years_net, years, wy_start_year, wy_start_month, wy_months, wife_gross_base, wife_rate
    )
    
    ### --- 연도별 시각화 ---
    if checked:
        for idx, label in enumerate(year_labels):
            if label in checked:
                df = pd.DataFrame({
                    "월": months,
                    "남편 실수령액": husband_years_net[idx],
                    "아내 실수령액": wife_years_net[idx],
                    "합계": np.array(husband_years_net[idx]) + np.array(wife_years_net[idx])
                })
                st.markdown(f"#### {label} 월별 실수령액")
                st.dataframe(df)
                fig, ax = plt.subplots(figsize=(8, 5))
                ax.bar(df["월"], df["남편 실수령액"], label="남편", color="#5B9BD5")
                ax.bar(df["월"], df["아내 실수령액"], label="아내", bottom=df["남편 실수령액"], color="#ED7D31")
                for i, total in enumerate(df["합계"]):
                    ax.text(i, total + 2, f"{int(total):,}만원", ha='center', va='bottom', fontsize=6, fontweight='bold')
                ax.set_ylabel("월별 실수령액(만원)")
                ax.set_xlabel("월")
                ax.set_title(f"{label} 부부 월별 실수령액 (Stacked, 육아휴직 반영)")
                ax.legend()
                st.pyplot(fig)
    
        # (나머지 코드 생략 - 기존 코드 복사)
        # 반드시 들여쓰기 맞추기!

# ---- 두번째 페이지 ----
elif page == "집 장만 시뮬레이션":
    st.title("🏠 신혼집 매매 vs 전세 시뮬레이션")
    house_mode = st.radio("주택 마련 방식 선택", ["매매", "전세"])
    if house_mode == "매매":
        st.subheader("🏡 집 매매 조건 입력")
        cash = st.number_input("보유 현금 (만원)", min_value=0, value=30000, step=100)
        house_price = st.number_input("집 매매가 (만원)", min_value=0, value=70000, step=500)
        need_loan = max(house_price - cash, 0)
        st.write(f"💡 예상 대출 필요금액: **{need_loan:,} 만원**")
        loan_year = st.slider("대출 만기 (년)", min_value=10, max_value=40, value=30)
        loan_rate = st.slider("금리 (%)", min_value=2.0, max_value=8.0, value=3.8, step=0.1)
        st.caption("※ 최근 2024년 기준 주담대 금리는 3.5~4.5%가 일반적이며, 신혼부부는 정책금리 활용도 고려하세요.")
        if house_price > 0:
            leverage = need_loan / house_price
            st.info(f"💸 **레버리지 비율: {leverage*100:.1f}%** (총 투자액 중 대출 비중)")
        st.markdown("#### 📈 집값 변동 시나리오")
        up_rate = st.slider("집값 연평균 상승률(%)", min_value=-5.0, max_value=10.0, value=3.0, step=0.1)
        dn_rate = st.slider("집값 연평균 하락률(%)", min_value=-10.0, max_value=0.0, value=-2.0, step=0.1)
        period = st.slider("시뮬레이션 기간 (년)", min_value=1, max_value=30, value=10)
        r = loan_rate / 100 / 12
        n = loan_year * 12
        if r > 0 and need_loan > 0:
            monthly_payment = need_loan * 10000 * r * (1 + r) ** n / ((1 + r) ** n - 1)
            monthly_payment = int(monthly_payment // 10) * 10
        else:
            monthly_payment = 0
        st.write(f"📅 매월 대출상환금(원리금균등): **{monthly_payment/10000:,.1f} 만원**")
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
            "연도": years,
            "집값(상승)": house_up,
            "집값(하락)": house_dn,
            "잔여대출": loan_balance,
            "순자산(상승시)": equity_up,
            "순자산(하락시)": equity_dn
        })
        
        st.dataframe(df.style.format({
            "집값(상승)": "{:,.0f}",
            "집값(하락)": "{:,.0f}",
            "잔여대출": "{:,.0f}",
            "순자산(상승시)": "{:,.0f}",
            "순자산(하락시)": "{:,.0f}",
        }))
        fig, ax = plt.subplots(figsize=(7, 4))
        ax.plot(df["연도"], df["순자산(상승시)"], marker='o', label="순자산(상승시)")
        ax.plot(df["연도"], df["순자산(하락시)"], marker='o', label="순자산(하락시)")
        ax.set_xlabel("경과연도")
        ax.set_ylabel("순자산(만원)")
        ax.set_title("부동산 시나리오별 순자산 변화")
        ax.legend()
        st.pyplot(fig)
        st.caption("""
        **참고:**  
        - 실제 세금, 취득세, 유지비, 임대료, 매도비용, 부부 소득·저축 등은 반영되지 않은 단순 시뮬레이션입니다.
        - 금리는 개인 신용/상품/정책에 따라 다를 수 있습니다.
        - 상승·하락률은 임의 시나리오입니다.  
        """)
    elif house_mode == "전세":
        st.subheader("🏡 전세 조건 입력")
        deposit = st.number_input("전세보증금 (만원)", min_value=0, value=40000, step=500)
        cash = st.number_input("보유 현금 (만원)", min_value=0, value=30000, step=100)
        st.info(f"전세보증금 {deposit:,} 만원, 추가현금 {cash:,} 만원 보유")
        st.markdown("""
        #### ⚖️ 전세는 원금손실/레버리지/시세차익 위험 없이 거주비만 고려됩니다.  
        - **전세 자산 = 보증금 + 보유현금**
        - 만기 반환/이사시, 집값변동 직접 영향 無  
        """)

# ---- 세번째 페이지 ----
elif page == "예상 가계부 시뮬레이션":

    st.title("📝 예상 가계부 시뮬레이션")

    husband_last_year = st.session_state.get("husband_last_year", 2033)
    wife_last_year = st.session_state.get("wife_last_year", 2033)
    final_year = max(husband_last_year, wife_last_year, 2033)
    start_year = 2024
    end_year = final_year
    months_sim = 12 * (end_year - start_year + 1)
    year_labels = [f"{y}년" for y in range(start_year, end_year+1)]
    month_labels = [f"{start_year + i//12}년 {i%12+1}월" for i in range(months_sim)]

    st.markdown("#### 1. 물가상승률")
    st.caption("💡 *한국 10년 평균 물가상승률(2014~2023): 약 2.2%*")
    inflation = st.slider("연간 물가상승률(%)", min_value=0.0, max_value=10.0, step=0.1, value=2.2)

    st.markdown("---")
    st.markdown("#### 2. 자녀 계획")
    num_children = st.selectbox("예상 자녀 수 (최대 3명)", [0, 1, 2, 3], index=1)
    child_plan = []
    for i in range(num_children):
        st.markdown(f"##### 자녀 {i+1} 출생 시점")
        colc1, colc2 = st.columns(2)
        with colc1:
            y = st.number_input(f"자녀 {i+1} 출생연도", min_value=2024, max_value=2050, value=2025)
        with colc2:
            m = st.selectbox(f"자녀 {i+1} 출생월", list(range(1, 13)), index=0, key=f"childm{i}")
        child_plan.append((y, m))
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

    # ---- 연도별 선택 ----
    checked = st.multiselect("확인하고 싶은 연도를 모두 선택하세요.", year_labels, default=[year_labels[0]])

    color_map = ["#5B9BD5", "#ED7D31", "#A9D18E", "#FFD966"]

    for year_label in checked:
        year = int(year_label.replace("년", ""))
        st.markdown(f"#### {year}년 월별 지출")
        idx_start = (year - start_year) * 12
        idx_end = idx_start + 12
        df_year = df.iloc[idx_start:idx_end].copy()
        df_year.reset_index(drop=True, inplace=True)

        # 시각화
        fig, ax = plt.subplots(figsize=(8, 5))
        x = np.arange(12)
        b1 = ax.bar(x, df_year["고정비합"], label="고정비", color=color_map[0])
        b2 = ax.bar(x, df_year["변동비합"], bottom=df_year["고정비합"], label="변동비", color=color_map[1])
        b3 = ax.bar(x, df_year["육아비합"], bottom=df_year["고정비합"]+df_year["변동비합"], label="육아비", color=color_map[2])
        b4 = ax.bar(x, df_year["주거비합"], bottom=df_year["고정비합"]+df_year["변동비합"]+df_year["육아비합"], label="주거비", color=color_map[3])

        # 합계 표기 (폰트 6)
        for i, total in enumerate(df_year["합계"]):
            ax.text(i, total + 2, f"{int(total):,}만원", ha='center', va='bottom', fontsize=6, fontweight='bold')
        ax.set_xticks(x)
        ax.set_xticklabels([f"{i+1}월" for i in range(12)])
        ax.set_ylabel("월별 지출(만원)")
        ax.set_xlabel("월")
        ax.set_title(f"{year}년 월별 지출 (스택그래프, 물가상승 반영)")
        ax.legend()
        st.pyplot(fig)
        st.dataframe(df_year)
    st.session_state['expense_df'] = df
    st.session_state['expense_total_arr'] = total_arr

    st.caption("""
    ※ 각 가계비용 예시는 평균값, 실제 소비패턴에 따라 달라질 수 있습니다.
    """)

# ---- 네번째 페이지 ----
elif page == "통합 자금흐름/잔액 분석":
    st.title("💰 통합 자금흐름/잔액 분석")

    # 데이터 준비: 세션에서 불러오기
    husband_years_net = st.session_state.get('husband_years_net')
    wife_years_net = st.session_state.get('wife_years_net')
    df = st.session_state.get('expense_df')

    # 값이 없는 경우 안내하고 코드 종료
    if husband_years_net is None or wife_years_net is None or df is None:
        st.error("❗️먼저 1페이지(월별 실수령액)와 3페이지(예상 가계부 시뮬레이션)에서 데이터를 입력/생성해 주세요!")
        st.stop()

    # 1. 단위 선택
    view_mode = st.radio("어떤 단위로 볼까요?", ["연도별", "월별"], horizontal=True)

    # 2. 항목 선택
    options = st.multiselect("보고싶은 항목을 선택하세요.", ["소득", "지출", "남는돈(저축가능액)"], default=["소득", "지출", "남는돈(저축가능액)"])

    # 월별 소득/지출/남는돈 준비
    # 월별 실수령액 리스트 (부부 합계)
    all_years = [int(y.replace("년","")) for y in year_labels]
    income_monthly = []
    for i, y in enumerate(all_years):
        income_monthly.extend(np.array(husband_years_net[i]) + np.array(wife_years_net[i]))
    expense_monthly = df["합계"].tolist()
    net_monthly = np.array(income_monthly) - np.array(expense_monthly)

    if view_mode == "연도별":
        # 연도별로 집계
        years = [int(y.replace("년", "")) for y in year_labels]
        n_years = len(years)
        income_annual = [sum(income_monthly[i*12:(i+1)*12]) for i in range(n_years)]
        expense_annual = [sum(expense_monthly[i*12:(i+1)*12]) for i in range(n_years)]
        net_annual = [sum(net_monthly[i*12:(i+1)*12]) for i in range(n_years)]

        # 선택한 항목 그래프
        width = 0.25
        x = np.arange(n_years)
        fig, ax = plt.subplots(figsize=(10, 6))
        offset = 0

        if "소득" in options:
            ax.bar(x + offset, income_annual, width, label="소득", color="#5B9BD5")
            offset += width
        if "지출" in options:
            ax.bar(x + offset, expense_annual, width, label="지출", color="#ED7D31")
            offset += width
        if "남는돈(저축가능액)" in options:
            ax.bar(x + offset, net_annual, width, label="남는돈", color="#A9D18E")
        ax.set_xticks(x + width)
        ax.set_xticklabels(year_labels, fontsize=10)
        ax.set_ylabel("연간 금액(만원)")
        ax.set_title("연도별 소득/지출/남는돈")
        ax.legend()
        st.pyplot(fig)
        # 테이블도 같이
        summary_df = pd.DataFrame({
            "연도": year_labels,
            "소득": income_annual,
            "지출": expense_annual,
            "남는돈": net_annual
        })
        st.dataframe(summary_df)
    else:
        # 월별 보기
        # 년도 선택
        min_year = all_years[0]
        max_year = all_years[-1]
        sel_start_year = st.selectbox("시작 연도", all_years, index=0)
        sel_period = st.slider("몇 년치 볼까요?", min_value=1, max_value=min(5, max_year-sel_start_year+1), value=1)
        start_idx = (sel_start_year - min_year) * 12
        end_idx = start_idx + sel_period*12

        sel_month_labels = month_labels[start_idx:end_idx]
        sel_income_monthly = income_monthly[start_idx:end_idx]
        sel_expense_monthly = expense_monthly[start_idx:end_idx]
        sel_net_monthly = net_monthly[start_idx:end_idx]

        # 항목별 월별 그래프
        fig, ax = plt.subplots(figsize=(max(10, sel_period*5), 5))
        x = np.arange(len(sel_month_labels))
        width = 0.25
        offset = 0
        label_shown = False
        if "소득" in options:
            ax.bar(x + offset, sel_income_monthly, width, label="소득", color="#5B9BD5")
            offset += width
            label_shown = True
        if "지출" in options:
            ax.bar(x + offset, sel_expense_monthly, width, label="지출", color="#ED7D31")
            offset += width
            label_shown = True
        if "남는돈(저축가능액)" in options:
            ax.bar(x + offset, sel_net_monthly, width, label="남는돈", color="#A9D18E")
            label_shown = True
        ax.set_xticks(x + width)
        # 월이 많으면 라벨 간격
        step = max(1, len(sel_month_labels)//20)
        ax.set_xticklabels([sel_month_labels[i] if i%step==0 else "" for i in range(len(sel_month_labels))], rotation=45, fontsize=8)
        ax.set_ylabel("월별 금액(만원)")
        ax.set_title(f"{sel_start_year}년~{sel_start_year+sel_period-1}년 월별 소득/지출/남는돈")
        if label_shown:
            ax.legend()
        st.pyplot(fig)

        # 누적 자금 그래프
        st.markdown("#### 💹 누적 유동현금흐름(남는돈 합계)")
        cumulative = np.cumsum(sel_net_monthly)
        fig2, ax2 = plt.subplots(figsize=(max(10, sel_period*5), 3))
        ax2.plot(x, cumulative, color="#7030A0", marker="o")
        ax2.axhline(0, color="gray", linestyle="--", linewidth=1)
        ax2.set_xticks(x)
        ax2.set_xticklabels([sel_month_labels[i] if i%step==0 else "" for i in range(len(sel_month_labels))], rotation=45, fontsize=8)
        ax2.set_ylabel("누적 자금(만원)")
        ax2.set_xlabel("월")
        ax2.set_title("누적 유동자금 흐름 (월별 남는돈 합계)")
        st.pyplot(fig2)

        # 월별 표도 함께
        month_df = pd.DataFrame({
            "월": sel_month_labels,
            "소득": sel_income_monthly,
            "지출": sel_expense_monthly,
            "남는돈": sel_net_monthly,
            "누적자금": cumulative
        })
        st.dataframe(month_df)

    st.caption("""
    ※ 실제 현금흐름은 대출 원금상환, 투자, 예적금, 보험해약, 세금, 기타 비정기 지출에 따라 크게 달라질 수 있습니다.
    """)


