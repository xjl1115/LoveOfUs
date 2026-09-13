<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Logo 区域 -->
      <div class="logo-section">
        <div class="logo-icon">❤️</div>
        <h1 class="logo-title">LoveOfUs</h1>
        <p class="logo-subtitle">记录我们的每一刻</p>
        <p class="logo-desc">时间会走，爱会停留</p>
      </div>

      <!-- 登录表单 -->
      <van-form class="login-form" @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.account"
            name="account"
            placeholder="手机号 / 邮箱"
            :rules="[
              { required: true, message: '请输入手机号或邮箱' },
              { validator: (val: string) => validateAccount(val.trim()).valid, message: '请输入有效的手机号或邮箱' }
            ]"
          >
            <template #left-icon>
              <van-icon name="user-o" />
            </template>
          </van-field>
          <!-- 密码输入区 / 验证码输入区 -->
          <van-field
            v-if="!isCodeLogin"
            v-model="form.password"
            :type="showLoginPassword ? 'text' : 'password'"
            name="password"
            placeholder="密码"
            :rules="[
              { required: true, message: '请输入密码' },
              { validator: validatePassword, message: '密码至少8位，包含大小写字母和数字' }
            ]"
          >
            <template #left-icon>
              <van-icon name="lock" />
            </template>
            <template #right-icon>
              <van-icon
                :name="showLoginPassword ? 'eye-o' : 'closed-eye'"
                @click="showLoginPassword = !showLoginPassword"
              />
            </template>
          </van-field>
          <!-- 验证码输入区 -->
          <van-field
            v-else
            v-model="form.code"
            name="code"
            placeholder="验证码"
            :rules="[{ required: true, message: '请输入验证码' }]"
          >
            <template #left-icon>
              <van-icon name="shield-o" />
            </template>
            <template #button>
              <van-button
                size="small"
                type="primary"
                :disabled="codeSending || codeCountdown > 0"
                @click="sendCode"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
              </van-button>
            </template>
          </van-field>
        </van-cell-group>

        <!-- 隐私协议复选框 -->
        <div class="agreement-section">
          <van-checkbox v-model="agreementChecked" shape="square" icon-size="16px">
            <template #default>
              <span class="agreement-text">
                我已阅读并同意
                <span class="agreement-link" @click.stop="showPrivacy = true">《隐私协议》</span>
                和
                <span class="agreement-link" @click.stop="showTerms = true">《用户协议》</span>
              </span>
            </template>
          </van-checkbox>
        </div>

        <div class="form-actions">
          <van-button
            round
            block
            type="primary"
            native-type="submit"
            :loading="loading"
            class="login-btn"
          >
            登 录
          </van-button>
        </div>

        <div class="form-links">
          <span class="link" @click="showRegister = true">没有账号？立即注册</span>
          <span class="link-divider">|</span>
          <span class="link" @click="showForgotPassword = true">忘记密码？</span>
        </div>
        <div class="switch-login-mode">
          <span v-if="!isCodeLogin" class="link" @click="isCodeLogin = true">使用验证码登录</span>
          <span v-else class="link" @click="isCodeLogin = false">使用密码登录</span>
        </div>
      </van-form>
    </div>

    <!-- 注册弹窗 -->
    <van-popup v-model:show="showRegister" round position="bottom" :style="{ height: '70%' }">
      <div class="register-popup">
        <div class="popup-header">
          <h3>注册账号</h3>
          <van-icon name="cross" @click="showRegister = false" />
        </div>
        <van-form @submit="onRegister">
          <van-cell-group inset>
            <van-field
            v-model="registerForm.nickname"
            name="nickname"
            placeholder="昵称"
            :rules="[
              { required: true, message: '请输入昵称' },
              { validator: validateNickname, message: '昵称2-20位，支持中文、字母、数字、下划线' }
            ]"
          />
            <van-field
              v-model="registerForm.account"
              name="account"
              placeholder="手机号 / 邮箱"
              :rules="[{ required: true, message: '请输入手机号或邮箱' }]"
            />
            <van-field
              v-model="registerForm.code"
              name="code"
              placeholder="验证码"
              :rules="[{ required: true, message: '请输入验证码' }]"
            >
              <template #button>
                <van-button
                  size="small"
                  type="primary"
                  :disabled="registerCodeSending || registerCodeCountdown > 0"
                  @click="sendRegisterCode"
                >
                  {{ registerCodeCountdown > 0 ? `${registerCodeCountdown}s` : '发送验证码' }}
                </van-button>
              </template>
            </van-field>
            <van-field
              v-model="registerForm.password"
              :type="showRegisterPassword ? 'text' : 'password'"
              name="password"
              placeholder="密码"
              :rules="[
                { required: true, message: '请输入密码' },
                { validator: validatePassword, message: '密码长度不能少于6位' }
              ]"
            >
              <template #right-icon>
                <van-icon
                  :name="showRegisterPassword ? 'eye-o' : 'closed-eye'"
                  @click="showRegisterPassword = !showRegisterPassword"
                />
              </template>
            </van-field>
            <van-field
              v-model="registerForm.confirmPassword"
              :type="showRegisterConfirmPassword ? 'text' : 'password'"
              name="confirmPassword"
              placeholder="确认密码"
              :rules="[
                { required: true, message: '请确认密码' },
                { validator: validateConfirmPassword, message: '两次密码不一致' }
              ]"
            >
              <template #right-icon>
                <van-icon
                  :name="showRegisterConfirmPassword ? 'eye-o' : 'closed-eye'"
                  @click="showRegisterConfirmPassword = !showRegisterConfirmPassword"
                />
              </template>
            </van-field>
            <van-field
              v-model="registerForm.bindCode"
              name="bindCode"
              placeholder="情侣绑定码（可选）"
            />
          </van-cell-group>
          <div class="form-actions">
            <van-button round block type="primary" native-type="submit" :loading="registerLoading">
              注 册
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 忘记密码弹窗 -->
    <van-popup v-model:show="showForgotPassword" round :style="{ width: '85%', maxWidth: '400px' }">
      <div class="forgot-password-popup">
        <div class="popup-header">
          <h3>忘记密码</h3>
          <van-icon name="cross" @click="showForgotPassword = false" />
        </div>
        <div class="forgot-password-options">
          <div
            class="option-item"
            :class="{ active: forgotPasswordType === 'code' }"
            @click="forgotPasswordType = 'code'"
          >
            <van-icon name="shield-o" />
            <span>验证码登录</span>
          </div>
          <div
            class="option-item"
            :class="{ active: forgotPasswordType === 'reset' }"
            @click="forgotPasswordType = 'reset'"
          >
            <van-icon name="lock" />
            <span>密码找回</span>
          </div>
        </div>
        <div class="popup-content">
          <p v-if="forgotPasswordType === 'code'" class="option-desc">
            选择验证码登录，将使用邮箱验证码直接登录
          </p>
          <p v-else class="option-desc">
            选择密码找回，将通过邮箱验证码重置您的密码
          </p>
        </div>
        <div class="popup-actions">
          <van-button round block type="primary" @click="handleForgotPasswordAction">
            {{ forgotPasswordType === 'code' ? '使用验证码登录' : '重置密码' }}
          </van-button>
        </div>
      </div>
    </van-popup>

    <!-- 重置密码弹窗 -->
    <van-popup v-model:show="showResetPassword" round :style="{ width: '85%', maxWidth: '400px' }">
      <div class="reset-password-popup">
        <div class="popup-header">
          <h3>重置密码</h3>
          <van-icon name="cross" @click="showResetPassword = false" />
        </div>
        <van-form @submit="onResetPassword">
          <van-cell-group inset>
            <van-field
              v-model="resetForm.account"
              name="account"
              placeholder="手机号 / 邮箱"
              :rules="[{ required: true, message: '请输入手机号或邮箱' }]"
            >
              <template #left-icon>
                <van-icon name="user-o" />
              </template>
            </van-field>
            <van-field
              v-model="resetForm.code"
              name="code"
              placeholder="验证码"
              :rules="[{ required: true, message: '请输入验证码' }]"
            >
              <template #left-icon>
                <van-icon name="shield-o" />
              </template>
              <template #button>
                <van-button
                  size="small"
                  type="primary"
                  :disabled="resetCodeSending || resetCodeCountdown > 0"
                  @click="sendResetCode"
                >
                  {{ resetCodeCountdown > 0 ? `${resetCodeCountdown}s` : '发送验证码' }}
                </van-button>
              </template>
            </van-field>
            <van-field
              v-model="resetForm.newPassword"
              :type="showResetNewPassword ? 'text' : 'password'"
              name="newPassword"
              placeholder="新密码"
              :rules="[
                { required: true, message: '请输入新密码' },
                { validator: validatePassword, message: '密码长度不能少于6位' }
              ]"
            >
              <template #left-icon>
                <van-icon name="lock" />
              </template>
              <template #right-icon>
                <van-icon
                  :name="showResetNewPassword ? 'eye-o' : 'closed-eye'"
                  @click="showResetNewPassword = !showResetNewPassword"
                />
              </template>
            </van-field>
            <van-field
              v-model="resetForm.confirmPassword"
              :type="showResetConfirmPassword ? 'text' : 'password'"
              name="confirmPassword"
              placeholder="确认新密码"
              :rules="[
                { required: true, message: '请确认新密码' },
                { validator: validateResetConfirmPassword, message: '两次密码不一致' }
              ]"
            >
              <template #left-icon>
                <van-icon name="lock" />
              </template>
              <template #right-icon>
                <van-icon
                  :name="showResetConfirmPassword ? 'eye-o' : 'closed-eye'"
                  @click="showResetConfirmPassword = !showResetConfirmPassword"
                />
              </template>
            </van-field>
          </van-cell-group>
          <div class="popup-actions">
            <van-button round block type="primary" native-type="submit" :loading="resetLoading">
              确认重置
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 隐私协议弹窗 -->
    <van-popup v-model:show="showPrivacy" round :style="{ width: '90%', height: '70%' }">
      <div class="agreement-popup">
        <div class="popup-header">
          <h3>隐私协议</h3>
          <van-icon name="cross" @click="showPrivacy = false" />
        </div>
        <div class="agreement-content">
          <p>欢迎使用 LoveOfUs（以下简称“本应用”）。本应用由 XJL科技有限公司（以下简称“我们”）开发并运营。我们深知个人信息对您的重要性，将严格依照《中华人民共和国个人信息保护法》《中华人民共和国网络安全法》《中华人民共和国数据安全法》等法律法规，采取相应的安全保护措施，保护您的个人信息安全。请您在使用本应用前仔细阅读并充分理解本政策。</p>
          <p><strong>一、我们收集的信息</strong></p>
          <p>1. 账号信息：您注册时提供的手机号或邮箱、昵称及登录密码。密码经加密处理后传输与存储，无法以明文还原。</p>
          <p>2. 个人资料：您后续主动补充的头像、性别、纪念日等个人资料信息。</p>
          <p>3. 身份验证信息：为保障账号安全，我们会在您登录、找回密码等场景下向您的手机或邮箱发送验证码，并对验证结果进行核验。</p>
          <p>4. 情侣关系信息：您主动填写的伴侣绑定码，以及与伴侣绑定后产生的关联关系数据。</p>
          <p>5. 照片与位置信息：您主动上传的照片及照片中包含的拍摄时间、地理位置等元数据，用于照片时间线、足迹地图与 AI 智能整理等核心功能。</p>
          <p>6. 聊天与 AI 信息：您发送的聊天消息（含文字、图片）；您在使用 AI 对话、照片分析、妆造建议等功能时提交的文本内容及授权处理的照片。</p>
          <p>7. 业务数据：相册、约会计划、愿望清单、心情记录、纪念日提醒、数据导出记录、通知设置等您在应用内产生的数据。</p>
          <p>8. 日志与设备信息：您访问本应用的时间、IP 地址、设备型号、操作系统及浏览器类型、页面访问记录等，用于安全防护、故障排查与体验优化。</p>
          <p><strong>二、我们如何使用信息</strong></p>
          <p>1. 提供、维护和改进本应用的核心功能与服务；</p>
          <p>2. 响应您的操作请求，包括发送验证码、推送纪念日提醒与通知、处理数据导出与账号注销；</p>
          <p>3. 开展安全风控，识别与防范账号被盗、恶意攻击、欺诈等安全风险；</p>
          <p>4. 在符合法律法规的前提下对脱敏后的数据进行分析以优化产品体验，分析结果不包含可识别您个人身份的信息。</p>
          <p><strong>三、Cookie 与本地存储</strong></p>
          <p>为向您提供更便捷的访问体验，我们会在您的设备中保存必要的登录凭证（Token）等本地数据。您可通过浏览器或设备设置管理、清除此类数据，但清除后可能影响部分功能的正常使用。</p>
          <p><strong>四、信息的存储与保护</strong></p>
          <p>1. 存储地点：您的个人信息存储于中华人民共和国境内的服务器，照片等文件存储于阿里云 OSS。</p>
          <p>2. 存储期限：我们仅在实现本政策所述目的所必需的期限内保留您的个人信息。您注销账号后，我们将依法删除或匿名化处理您的个人信息，法律法规另有规定的除外。</p>
          <p>3. 安全措施：我们采用传输加密（HTTPS/TLS）、访问权限控制、密码加密存储、敏感操作校验等技术与管理措施保护您的个人信息，并定期开展安全评估。</p>
          <p><strong>五、信息的共享、转让与公开披露</strong></p>
          <p>1. 我们不会向任何第三方出售您的个人信息。</p>
          <p>2. 为实现本政策所述功能，我们仅在必要范围内向下列服务提供商提供相关信息，并要求其依法依约处理：</p>
          <p>(1) 阿里云 OSS：用于存储您上传的照片等文件；</p>
          <p>(2) 阿里云 DashScope（通义千问）：用于提供 AI 对话、照片分析与妆造建议，相关文本与照片按需传输至该服务处理；</p>
          <p>(3) 邮件及短信服务商：用于发送验证码与系统通知。</p>
          <p>3. 除上述情形外，我们仅在取得您的明确同意、依据法律法规或司法/行政机关的强制性要求，或为维护您及他人生命财产安全等重大合法权益所必需时，才会共享、转让或公开披露您的信息。</p>
          <p><strong>六、您的权利</strong></p>
          <p>1. 查询与更正：您可在个人资料页查询并更正您的昵称、头像、性别等信息。</p>
          <p>2. 删除：您可以删除自己上传的照片、相册及聊天记录。</p>
          <p>3. 撤回同意与注销：您可随时在设置中注销账号并撤回同意，注销后我们将依法删除或匿名化处理您的个人信息。</p>
          <p>4. 数据导出：您可使用应用内的数据导出功能获取您的个人数据副本。</p>
          <p>5. 投诉与举报：如您对个人信息处理活动有异议，可通过本政策载明的联系方式提出，我们将在十五个工作日内予以答复。</p>
          <p><strong>七、未成年人保护</strong></p>
          <p>本应用面向成年人提供服务。若您为未满十四周岁的儿童，请在监护人陪同下阅读本政策，并在征得监护人明确同意后使用本应用；若您为十四周岁至十八周岁的未成年人，请在监护人指导下使用。</p>
          <p><strong>八、本政策的更新</strong></p>
          <p>我们可能适时修订本政策。涉及对您权益产生重大影响的变更，我们将通过应用内显著提示等方式通知您。如您继续使用本应用，视为接受修订后的政策。</p>
          <p><strong>九、联系我们</strong></p>
          <p>如您对本政策或个人信息保护有任何疑问、意见或投诉，请通过以下方式与我们联系：</p>
          <p>运营方：XJL科技有限公司</p>
          <p>联系邮箱：xjl20041115@126.com</p>
          <p>我们将在十五个工作日内核实并处理您的请求。</p>
        </div>
      </div>
    </van-popup>

    <!-- 用户协议弹窗 -->
    <van-popup v-model:show="showTerms" round :style="{ width: '90%', height: '70%' }">
      <div class="agreement-popup">
        <div class="popup-header">
          <h3>用户协议</h3>
          <van-icon name="cross" @click="showTerms = false" />
        </div>
        <div class="agreement-content">
          <p>欢迎使用 LoveOfUs（以下简称“本应用”）。本应用由 XJL科技有限公司（以下简称“我们”）开发并运营。本协议是您与我们之间就使用本应用所订立的、具有法律约束力的协议。您通过注册、登录、勾选同意或实际使用本应用的行为，即视为您已阅读、理解并同意本协议全部内容。</p>
          <p><strong>一、总则</strong></p>
          <p>1. 本协议适用于您通过网页端、移动端等方式使用本应用提供的全部产品与服务。</p>
          <p>2. 您在使用本应用前，应同时阅读并同意《隐私协议》；本协议与《隐私协议》共同构成您与我们之间的完整约定。</p>
          <p><strong>二、服务内容</strong></p>
          <p>本应用为情侣/夫妻提供照片记录与整理、足迹地图、纪念日管理、即时聊天、AI 智能辅助、约会计划、愿望清单、心情记录及数据导出等功能。我们可能根据业务发展调整、新增或停止部分功能，具体以应用内实际提供的服务为准。</p>
          <p><strong>三、账号的注册与管理</strong></p>
          <p>1. 注册条件：您确认已年满十八周岁，具备完全民事行为能力。注册时需提供手机号或邮箱、昵称并设置密码，通过验证码验证后完成注册。</p>
          <p>2. 账号安全：您应妥善保管账号、密码及验证码，不得出借、转让或与他人共享账号。因您保管不善导致的损失由您自行承担。</p>
          <p>3. 伴侣绑定：您可通过绑定码与伴侣建立情侣关系。绑定成功后，双方将在授权范围内互相查看照片、相册、聊天记录与纪念日等信息，请您在确认对方身份后谨慎操作。</p>
          <p>4. 账号异常：如您发现账号存在未经授权的使用或安全漏洞，应立即通知我们，我们将协助您采取必要措施。</p>
          <p><strong>四、用户行为规范</strong></p>
          <p>您承诺在使用本应用过程中遵守法律法规及本协议约定，不得从事以下行为：</p>
          <p>1. 上传、发布违法、色情、暴力、恐怖、侮辱诽谤、侵犯他人隐私或知识产权的内容；</p>
          <p>2. 未经授权收集、使用、传播其他用户的个人信息；</p>
          <p>3. 利用本应用从事诈骗、赌博、洗钱等违法犯罪活动；</p>
          <p>4. 通过爬虫、外挂、脚本或其他技术手段恶意访问、攻击、干扰本应用的正常运行；</p>
          <p>5. 对本应用软件进行反向工程、反编译、破解或以其他方式窃取源代码；</p>
          <p>6. 其他违反法律法规或损害我们及第三方合法权益的行为。</p>
          <p>如您违反上述约定，我们有权视情节采取删除内容、限制功能、暂停或终止账号等措施，并保留追究法律责任的权利。</p>
          <p><strong>五、知识产权</strong></p>
          <p>1. 您对自己上传、发布的内容（含照片、文字、图片等）依法享有相应权利，并授予我们为提供本服务所必需的存储、展示、处理（含 AI 分析）等非独占、可撤销的权利。</p>
          <p>2. 本应用及其相关软件的界面设计、代码、商标、标识等知识产权归我们或相应权利人所有。未经书面许可，您不得复制、修改、传播或用于任何商业用途。</p>
          <p>3. 您向我们提交的反馈、建议等不构成对您知识产权的侵犯，我们可在服务改进范围内合理使用。</p>
          <p><strong>六、隐私保护</strong></p>
          <p>我们重视您的个人信息保护，具体的信息收集、使用、存储与共享规则详见本应用中的《隐私协议》。您在使用本应用前应一并阅读并同意《隐私协议》。</p>
          <p><strong>七、服务的变更、中断与终止</strong></p>
          <p>1. 因业务调整、技术升级、系统维护等原因，我们可能变更、暂停或终止部分服务，并将尽可能通过应用内公告等方式提前通知。</p>
          <p>2. 若您违反本协议或相关法律法规，我们有权限制或终止向您提供服务，由此产生的后果由您自行承担。</p>
          <p>3. 您可随时在设置中申请注销账号，注销后我们将按《隐私协议》的约定处理您的个人信息。</p>
          <p><strong>八、免责声明</strong></p>
          <p>1. 因不可抗力（包括但不限于自然灾害、战争、政策调整、运营商网络故障）或第三方服务中断导致的服务异常，我们不承担超出法律规定的责任。</p>
          <p>2. AI 生成的内容（含文字、图片）仅供娱乐与参考，不构成医疗、法律、金融等任何专业建议，请您结合实际情况审慎判断。</p>
          <p>3. 您应对自行上传的内容及通过本应用进行的操作负责，因您个人原因造成的损失由您自行承担。</p>
          <p><strong>九、违约责任</strong></p>
          <p>因您违反本协议或法律法规给第三方造成损害的，由您依法承担相应责任；给我们造成损失的，您应赔偿由此产生的合理损失（含合理的维权费用）。</p>
          <p><strong>十、协议的修改</strong></p>
          <p>我们可能不时修订本协议。修订后的协议将在应用内提示，如您继续使用本应用，视为接受修订后的协议。对您权益产生重大影响的变更，我们将以显著方式另行通知。</p>
          <p><strong>十一、法律适用与争议解决</strong></p>
          <p>本协议的订立、履行与解释均适用中华人民共和国法律。因本协议产生的争议，双方应友好协商解决；协商不成的，任何一方均可向运营方所在地有管辖权的人民法院提起诉讼。</p>
          <p><strong>十二、联系我们</strong></p>
          <p>如您对本协议有任何疑问或建议，可通过以下方式与我们联系：</p>
          <p>运营方：XJL科技有限公司</p>
          <p>联系邮箱：xjl20041115@126.com</p>
          <p>我们将在合理时间内（一般不超过十五个工作日）予以回复。</p>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'
import { login, register } from '@/api/user'
import { sendCaptcha, resetPassword } from '@/api/auth'
import { hashPassword } from '@/utils/crypto'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const showRegister = ref(false)
const registerLoading = ref(false)

// 隐私协议相关
const agreementChecked = ref(false)
const showPrivacy = ref(false)
const showTerms = ref(false)

// 验证码登录相关
const isCodeLogin = ref(false)
const codeSending = ref(false)
const codeCountdown = ref(0)

// 忘记密码相关
const showForgotPassword = ref(false)
const forgotPasswordType = ref<'code' | 'reset'>('code')
const showResetPassword = ref(false)
const resetCodeSending = ref(false)
const resetCodeCountdown = ref(0)
const resetLoading = ref(false)

// 密码显示/隐藏
const showLoginPassword = ref(false)
const showRegisterPassword = ref(false)
const showRegisterConfirmPassword = ref(false)
const showResetNewPassword = ref(false)
const showResetConfirmPassword = ref(false)

const form = reactive({
  account: '',
  password: '',
  code: ''
})

const registerForm = reactive({
  nickname: '',
  account: '',
  code: '',
  password: '',
  confirmPassword: '',
  bindCode: ''
})

// 注册验证码相关
const registerCodeSending = ref(false)
const registerCodeCountdown = ref(0)

const resetForm = reactive({
  account: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

// ==================== 输入验证工具函数 ====================

/** 验证邮箱格式 */
const validateEmail = (email: string): boolean => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}

/** 验证手机号格式（中国大陆） */
const validatePhone = (phone: string): boolean => {
  const phoneRegex = /^1[3-9]\d{9}$/
  return phoneRegex.test(phone)
}

/** 验证账号类型（邮箱或手机号） */
const validateAccount = (account: string): { valid: boolean; type: 'email' | 'phone' | 'invalid' } => {
  if (validateEmail(account)) {
    return { valid: true, type: 'email' }
  }
  if (validatePhone(account)) {
    return { valid: true, type: 'phone' }
  }
  return { valid: false, type: 'invalid' }
}

/** 验证密码复杂度
 * 要求：至少6位
 */
const validatePassword = (val: string): boolean => {
  // 至少6位
  return val.length >= 6
}

/** 验证密码复杂度（带错误信息） */
const validatePasswordWithMessage = (val: string): string | boolean => {
  if (val.length < 6) {
    return '密码长度至少6位'
  }
  return true
}

/** 验证确认密码 */
const validateConfirmPassword = () => {
  return registerForm.password === registerForm.confirmPassword
}

/** 验证重置密码确认 */
const validateResetConfirmPassword = () => {
  return resetForm.newPassword === resetForm.confirmPassword
}

/** 验证昵称 */
const validateNickname = (nickname: string): boolean => {
  // 2-20位，支持中文、字母、数字、下划线
  const nicknameRegex = /^[\u4e00-\u9fa5a-zA-Z0-9_]{2,20}$/
  return nicknameRegex.test(nickname)
}

/** 验证验证码 */
const validateCaptcha = (code: string): boolean => {
  // 6位数字
  const captchaRegex = /^\d{6}$/
  return captchaRegex.test(code)
}

/** 验证绑定码 */
const validateBindCode = (code: string): boolean => {
  if (!code) return true // 可选字段
  // 6-20位字母数字
  const bindCodeRegex = /^[a-zA-Z0-9]{6,20}$/
  return bindCodeRegex.test(code)
}

const onSubmit = async () => {
  if (!agreementChecked.value) {
    showToast('请先同意隐私协议和用户协议')
    return
  }

  // 验证账号格式
  const accountValidation = validateAccount(form.account.trim())
  if (!accountValidation.valid) {
    showToast('请输入有效的手机号或邮箱')
    return
  }

  // 验证码登录时验证验证码格式
  if (isCodeLogin.value && !validateCaptcha(form.code)) {
    showToast('请输入6位数字验证码')
    return
  }

  // 密码登录时验证密码格式
  if (!isCodeLogin.value) {
    const passwordCheck = validatePasswordWithMessage(form.password)
    if (typeof passwordCheck === 'string') {
      showToast(passwordCheck)
      return
    }
  }

  loading.value = true
  try {
    const loginData: any = {
      account: form.account.trim(),
      loginType: isCodeLogin.value ? 'captcha' : 'password'
    }

    if (isCodeLogin.value) {
      loginData.captcha = form.code
    } else {
      // 密码进行 SHA-256 加密后传输
      loginData.password = await hashPassword(form.password)
    }

    const data = await login(loginData)
    userStore.setTokenWithExpire(data.token, data.expiresIn)
    if (data.refreshToken) {
      userStore.setRefreshToken(data.refreshToken)
    }
    userStore.setUserInfo(data.userInfo)
    showToast('登录成功')
    router.push('/home')
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

const onRegister = async () => {
  // 验证昵称
  if (!validateNickname(registerForm.nickname.trim())) {
    showToast('昵称必须为2-20位，支持中文、字母、数字、下划线')
    return
  }

  // 验证账号格式
  const accountValidation = validateAccount(registerForm.account.trim())
  if (!accountValidation.valid) {
    showToast('请输入有效的手机号或邮箱')
    return
  }

  // 验证验证码格式
  if (!validateCaptcha(registerForm.code)) {
    showToast('请输入6位数字验证码')
    return
  }

  // 验证密码复杂度
  const passwordCheck = validatePasswordWithMessage(registerForm.password)
  if (typeof passwordCheck === 'string') {
    showToast(passwordCheck)
    return
  }

  // 验证确认密码
  if (!validateConfirmPassword()) {
    showToast('两次输入的密码不一致')
    return
  }

  // 验证绑定码（如果填写了）
  if (registerForm.bindCode && !validateBindCode(registerForm.bindCode.trim())) {
    showToast('绑定码必须为6-20位字母数字组合')
    return
  }

  registerLoading.value = true
  try {
    const isEmail = registerForm.account.includes('@')
    // 密码和确认密码均进行 SHA-256 加密后传输
    const encryptedPassword = await hashPassword(registerForm.password)
    const encryptedConfirmPassword = await hashPassword(registerForm.confirmPassword)
    await register({
      nickname: registerForm.nickname.trim(),
      [isEmail ? 'email' : 'phone']: registerForm.account.trim(),
      password: encryptedPassword,
      confirmPassword: encryptedConfirmPassword,
      captcha: registerForm.code,
      partnerCode: registerForm.bindCode?.trim() || undefined
    })
    showToast('注册成功，请登录')
    // 将注册的账号填入登录表单
    form.account = registerForm.account
    showRegister.value = false
  } catch (error) {
    console.error('注册失败:', error)
  } finally {
    registerLoading.value = false
  }
}

// 发送登录验证码
const sendCode = async () => {
  if (!form.account.trim()) {
    showToast('请先输入手机号或邮箱')
    return
  }
  codeSending.value = true
  try {
    const isEmail = form.account.includes('@')
    await sendCaptcha({
      target: form.account.trim(),
      channel: isEmail ? 'email' : 'sms',
      type: 'login'
    })
    showToast('验证码已发送')
    codeCountdown.value = 60
    const timer = setInterval(() => {
      codeCountdown.value--
      if (codeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    showToast('发送验证码失败，请稍后重试')
  } finally {
    codeSending.value = false
  }
}

// 发送注册验证码
const sendRegisterCode = async () => {
  if (!registerForm.account.trim()) {
    showToast('请先输入手机号或邮箱')
    return
  }
  registerCodeSending.value = true
  try {
    const isEmail = registerForm.account.includes('@')
    await sendCaptcha({
      target: registerForm.account.trim(),
      channel: isEmail ? 'email' : 'sms',
      type: 'register'
    })
    showToast('验证码已发送')
    registerCodeCountdown.value = 60
    const timer = setInterval(() => {
      registerCodeCountdown.value--
      if (registerCodeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    showToast('发送验证码失败，请稍后重试')
  } finally {
    registerCodeSending.value = false
  }
}

// 处理忘记密码选项
const handleForgotPasswordAction = () => {
  showForgotPassword.value = false
  if (forgotPasswordType.value === 'code') {
    isCodeLogin.value = true
    showToast('已切换至验证码登录模式')
  } else {
    showResetPassword.value = true
  }
}

// 发送重置密码验证码
const sendResetCode = async () => {
  if (!resetForm.account.trim()) {
    showToast('请先输入手机号或邮箱')
    return
  }
  resetCodeSending.value = true
  try {
    const isEmail = resetForm.account.includes('@')
    await sendCaptcha({
      target: resetForm.account.trim(),
      channel: isEmail ? 'email' : 'sms',
      type: 'reset_password'
    })
    showToast('验证码已发送')
    resetCodeCountdown.value = 60
    const timer = setInterval(() => {
      resetCodeCountdown.value--
      if (resetCodeCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    console.error('发送验证码失败:', error)
    showToast('发送验证码失败，请稍后重试')
  } finally {
    resetCodeSending.value = false
  }
}

// 重置密码
const onResetPassword = async () => {
  if (!resetForm.newPassword || !resetForm.confirmPassword) {
    showToast('请输入密码')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    showToast('两次密码不一致')
    return
  }
  if (resetForm.newPassword.length < 6 || resetForm.newPassword.length > 20) {
    showToast('密码长度需在6-20位之间')
    return
  }
  if (!form.account) {
    showToast('请先输入手机号或邮箱')
    return
  }
  resetLoading.value = true
  try {
    const isEmail = form.account.includes('@')
    const encryptedPassword = await hashPassword(resetForm.newPassword)
    const encryptedConfirmPassword = await hashPassword(resetForm.confirmPassword)
    await resetPassword({
      target: form.account,
      channel: isEmail ? 'email' : 'sms',
      captcha: resetForm.code,
      newPassword: encryptedPassword,
      confirmPassword: encryptedConfirmPassword
    })
    showToast('密码重置成功')
    showResetPassword.value = false
    resetForm.code = ''
    resetForm.newPassword = ''
    resetForm.confirmPassword = ''
  } catch (error) {
    console.error('重置密码失败:', error)
  } finally {
    resetLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #FFF7EA 0%, #FFE4D6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-container {
  width: 100%;
  max-width: 400px;
}

.logo-section {
  text-align: center;
  margin-bottom: 40px;

  .logo-icon {
    font-size: 64px;
    margin-bottom: 16px;
    animation: heartbeat 1.5s ease-in-out infinite;
  }

  .logo-title {
    font-size: 28px;
    font-weight: 600;
    color: $primary-color;
    margin-bottom: 8px;
  }

  .logo-subtitle {
    font-size: 18px;
    color: $text-primary;
    margin-bottom: 4px;
  }

  .logo-desc {
    font-size: 14px;
    color: $text-tertiary;
  }
}

@keyframes heartbeat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.login-form {
  .agreement-section {
    margin: 12px 16px;
    display: flex;
    align-items: flex-start;

    .agreement-text {
      font-size: 12px;
      color: $text-secondary;
      line-height: 1.5;
    }

    .agreement-link {
      color: $primary-color;
      cursor: pointer;
    }
  }

  .form-actions {
    margin: 24px 16px 16px;
  }

  .login-btn {
    background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
    border: none;
  }

  .form-links {
    text-align: center;
    margin-top: 16px;

    .link {
      color: $primary-color;
      font-size: 14px;
      cursor: pointer;
    }

    .link-divider {
      color: $primary-light;
      margin: 0 12px;
      font-weight: 300;
      opacity: 0.6;
      user-select: none;
    }
  }

  .switch-login-mode {
    text-align: center;
    margin-top: 12px;

    .link {
      color: $primary-color;
      font-size: 13px;
      cursor: pointer;
    }
  }
}

.register-popup {
  height: 100%;
  background: $bg-white;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .form-actions {
    margin: 24px 16px;
  }
}

.forgot-password-popup {
  background: $bg-white;
  padding-bottom: 20px;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .forgot-password-options {
    display: flex;
    padding: 20px 16px;
    gap: 12px;

    .option-item {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 16px;
      border: 1px solid $border-color;
      border-radius: $radius-md;
      cursor: pointer;
      transition: all 0.3s;

      .van-icon {
        font-size: 24px;
        color: $text-secondary;
      }

      span {
        font-size: 14px;
        color: $text-secondary;
      }

      &.active {
        border-color: $primary-color;
        background: rgba($primary-color, 0.05);

        .van-icon,
        span {
          color: $primary-color;
        }
      }
    }
  }

  .popup-content {
    padding: 0 16px 16px;

    .option-desc {
      font-size: 13px;
      color: $text-tertiary;
      text-align: center;
      margin: 0;
    }
  }

  .popup-actions {
    padding: 0 16px;
  }
}

.reset-password-popup {
  background: $bg-white;
  padding-bottom: 20px;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .popup-actions {
    padding: 20px 16px 0;
  }
}

.agreement-popup {
  height: 100%;
  background: $bg-white;
  display: flex;
  flex-direction: column;

  .popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    border-bottom: 1px solid $border-color;
    flex-shrink: 0;

    h3 {
      margin: 0;
      font-size: 18px;
    }

    .van-icon {
      font-size: 20px;
      color: $text-secondary;
      cursor: pointer;
    }
  }

  .agreement-content {
    flex: 1;
    padding: 16px;
    overflow-y: auto;

    p {
      font-size: 14px;
      color: $text-primary;
      line-height: 1.6;
    }
  }
}

@media (min-width: 768px) {
  .login-container {
    background: $bg-white;
    padding: 40px;
    border-radius: $radius-xl;
    box-shadow: $shadow-lg;
  }
}
</style>
